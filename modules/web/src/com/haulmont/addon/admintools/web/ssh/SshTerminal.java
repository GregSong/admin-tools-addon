package com.haulmont.addon.admintools.web.ssh;

import com.haulmont.addon.admintools.global.ssh.SshCredential;
import com.haulmont.addon.admintools.gui.xterm.components.EnterReactivePasswordField;
import com.haulmont.addon.admintools.gui.xterm.components.XtermJs;
import com.haulmont.addon.admintools.web.utils.NonBlockingIOUtils;
import com.haulmont.cuba.core.entity.FileDescriptor;
import com.haulmont.cuba.core.global.FileLoader;
import com.haulmont.cuba.core.global.FileStorageException;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.executors.BackgroundTask;
import com.haulmont.cuba.gui.executors.BackgroundTaskHandler;
import com.haulmont.cuba.gui.executors.BackgroundWorker;
import com.haulmont.cuba.gui.executors.TaskLifeCycle;
import com.haulmont.cuba.gui.xml.layout.ComponentsFactory;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.UUID;

import static com.haulmont.cuba.gui.components.Frame.NotificationType.WARNING;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.IOUtils.toByteArray;
import static org.apache.commons.lang.StringUtils.isBlank;

public class SshTerminal extends AbstractWindow {

    public static final Integer CONNECTION_TIMEOUT_SECONDS = 22;

    private Logger log = LoggerFactory.getLogger(SshTerminal.class);

    @Inject
    protected Metadata metadata;
    @Inject
    protected ComponentsFactory componentsFactory;
    @Inject
    protected Datasource<SshCredential> sshCredentialDs;
    @Inject
    protected CollectionDatasource<SshCredential, UUID> sshCredentialListDs;
    @Inject
    protected FieldGroup fieldGroup;
    @Inject
    protected TextField sessionNameField;
    @Inject
    protected OptionsList optionsList;
    @Inject
    protected FileLoader fileLoader;
    @Inject
    protected ProgressBar terminalProgressBar;
    @Inject
    protected XtermJs terminal;
    @Inject
    protected BackgroundWorker backgroundWorker;

    protected JSch jsch = new JSch();
    protected Session session;
    protected ChannelShell mainChannel;
    protected InputStream mainIn;
    protected PrintStream mainOut;

    protected NonBlockingIOUtils ioUtils = new NonBlockingIOUtils();
    protected BackgroundTask<Integer, Void> connectionTask;
    protected BackgroundTaskHandler connectionTaskHandler;
    protected SshCredential credentials;
    protected TextField hostnameField;

    @Override
    public void init(Map<String, Object> params) {
        SshCredential credentials = metadata.create(SshCredential.class);
        sshCredentialDs.setItem(credentials);

        connectionTask = new BackgroundTask<Integer, Void>(CONNECTION_TIMEOUT_SECONDS, getFrame()) {
            @Override
            public Void run(TaskLifeCycle<Integer> taskLifeCycle) throws JSchException, IOException, FileStorageException {
                internalConnect();
                if (connectionTaskHandler.isCancelled()) {
                    disconnectSsh();
                }
                return null;
            }

            @Override
            public boolean handleException(Exception ex) {
                terminal.writeln(formatMessage("console.error", ex.getMessage()));
                disconnectSsh();
                terminalProgressBar.setIndeterminate(false);
                log.info("User can't create ssh connection", ex);
                return true;
            }

            @Override
            public void canceled() {
                terminal.writeln(formatMessage("console.disconnected", sshCredentialDs.getItem().getHostname()));
                terminalProgressBar.setIndeterminate(false);
            }

            @Override
            public boolean handleTimeoutException() {
                terminal.writeln(formatMessage("console.disconnected.timeout", sshCredentialDs.getItem().getHostname()));
                terminalProgressBar.setIndeterminate(false);
                return true;
            }

            @Override
            public void done(Void result) {
                terminalProgressBar.setIndeterminate(false);
            }
        };

        terminal.setDataListener(this::terminalDataListener);
        terminal.setSizeListener(this::terminalSizeListener);
    }

    @Override
    public void ready() {
        hostnameField.requestFocus();
    }

    @Override
    protected boolean preClose(String actionId) {
        if (isBackgroundTaskExecuted()) {
            connectionTaskHandler.cancel();
            showNotification(formatMessage("console.disconnected", sshCredentialDs.getItem().getHostname()));
        } else {
            if (isMainChannelOpen()) {
                mainChannel.disconnect();
            }
            if (isSessionOpen()) {
                session.disconnect();
                showNotification(formatMessage("console.disconnected", sshCredentialDs.getItem().getHostname()));
            }
        }
        return super.preClose(actionId);
    }

    protected void terminalDataListener(String data) {
        if (!isMainChannelOpen()) {
            return;
        }

        mainOut.append(data);
        mainOut.flush();
    }

    protected void terminalSizeListener(int cols, int rows) {
        if (isMainChannelOpen()) {
            mainChannel.setPtySize(cols, rows, 640, 480);
        }
    }

    protected boolean isMainChannelOpen() {
        return mainChannel != null && mainChannel.isConnected();
    }

    protected boolean isSessionOpen() {
        return session != null && session.isConnected();
    }

    protected boolean isBackgroundTaskExecuted() {
        return connectionTaskHandler != null && connectionTaskHandler.isAlive();
    }

    public void connect() {
        if (!validateAll() || isBackgroundTaskExecuted()) {
            return;
        }

        if (isMainChannelOpen()) {
            showOptionDialog(getMessage("confirmReconnect.title"), getMessage("confirmReconnect.msg"),
                    MessageType.CONFIRMATION, new Action[]{
                            new DialogAction(DialogAction.Type.YES, Action.Status.PRIMARY).withHandler(e -> {
                                disconnectSsh();
                                executeConnectionProgressTask();
                            }),
                            new DialogAction(DialogAction.Type.CANCEL, Action.Status.NORMAL)
                    });
        } else {
            executeConnectionProgressTask();
        }

        // resolve problem with size of console
        terminal.fit();
    }

    protected void executeConnectionProgressTask() {
        credentials = sshCredentialDs.getItem();
        terminalProgressBar.setIndeterminate(true);
        connectionTaskHandler = backgroundWorker.handle(connectionTask);
        connectionTaskHandler.execute();
        terminal.writeln(formatMessage("console.connected", credentials.getHostname()));
    }

    protected void internalConnect() throws JSchException, IOException, FileStorageException {
        session = getSession();
        session.connect();

        mainChannel = (ChannelShell) session.openChannel("shell");
        mainChannel.setPtyType("xterm");
        mainChannel.setEnv("LANG", "en_US.UTF-8");
        mainChannel.connect();

        mainOut = new PrintStream(mainChannel.getOutputStream());
        mainIn = mainChannel.getInputStream();
    }

    protected Session getSession() throws JSchException, IOException, FileStorageException {
        FileDescriptor privateKey = credentials.getPrivateKey();

        if (privateKey != null) {
            try (InputStream inputStream = fileLoader.openStream(privateKey)) {
                byte[] privateKeyBytes = toByteArray(inputStream);
                byte[] passphraseBytes = credentials.getPassphrase() == null ? null : credentials.getPassphrase().getBytes();
                jsch.addIdentity(credentials.getHostname(), privateKeyBytes, null, passphraseBytes);
            }
        }

        Session session = jsch.getSession(credentials.getLogin(), credentials.getHostname(), credentials.getPort());

        if (privateKey == null) {
            session.setPassword(credentials.getPassword());
        }

        session.setConfig("StrictHostKeyChecking", "no");
        return session;
    }

    public Component generateHostnameField(Datasource datasource, String fieldId) {
        hostnameField = componentsFactory.createComponent(TextField.class);
        hostnameField.setDatasource(datasource, "hostname");
        hostnameField.setWidth("70%");

        TextField portField = componentsFactory.createComponent(TextField.class);
        portField.setDatasource(datasource, "port");
        portField.setWidth("60px");

        HBoxLayout hostnameBox = componentsFactory.createComponent(HBoxLayout.class);

        hostnameBox.add(hostnameField);
        hostnameBox.add(portField);

        hostnameBox.expand(hostnameField);
        hostnameBox.setSpacing(true);

        return hostnameBox;
    }

    public Component generatePasswordField(Datasource datasource, String fieldId) {
        EnterReactivePasswordField component = componentsFactory.createComponent(EnterReactivePasswordField.class);
        component.addEnterPressListener(e -> connect());
        component.setDatasource(datasource, fieldId);
        return component;
    }

    public void disconnect() {
        if (isBackgroundTaskExecuted()) {
            connectionTaskHandler.cancel();
        } else disconnectSsh();
    }

    protected void disconnectSsh() {
        if (isMainChannelOpen()) {
            mainChannel.disconnect();
        }
        if (isSessionOpen()) {
            session.disconnect();
            terminal.writeln(formatMessage("console.disconnected", sshCredentialDs.getItem().getHostname()));
        }
    }

    public void onUpdateConsole(Timer source) throws IOException {
        if (!isMainChannelOpen()) {
            return;
        }

        terminal.write(ioUtils.toStringWithBarrier(mainIn, UTF_8, 100));
    }


    public void onFitBtnClick() {
        terminal.fit();
    }

    public void loadCredential() {
        SshCredential credential = optionsList.getValue();

        if(credential!=null){
            sshCredentialDs.setItem(credential);
            sshCredentialDs.refresh();
            sessionNameField.setValue(credential.getSessionName());
        }
    }

    public void saveCredential() {
        if(!fieldGroup.isValid()){
            showNotification(getMessage("credetialsNotValid"), WARNING);
            return;
        }

        SshCredential item = sshCredentialDs.getItem();

        String sessionName = sessionNameField.getRawValue();
        if(isBlank(sessionName)){
            item.setSessionName(format("%s@%s", item.getLogin(), item.getHostname()));
        } else {
            item.setSessionName(sessionName);
        }

       if(sshCredentialListDs.containsItem(item.getUuid())){
           sshCredentialListDs.modifyItem(item);
       } else {
           sshCredentialListDs.addItem(item);
       }

        sshCredentialListDs.commit();
        sshCredentialListDs.refresh();
        optionsList.setValue(item);
        sshCredentialDs.setItem(metadata.create(SshCredential.class));
    }

    public void removeCredential() {
        SshCredential credential = optionsList.getValue();

        if(credential!=null){
            optionsList.setValue(null);
            SshCredential formItem = sshCredentialDs.getItem();

            if(formItem.equals(credential)){
                sshCredentialDs.setItem(metadata.create(SshCredential.class));
            }

            sshCredentialListDs.removeItem(credential);
            sshCredentialListDs.commit();
            sshCredentialListDs.refresh();
        }
    }
}