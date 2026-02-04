import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

public class EmailNotifier implements Runnable {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withLocale(Locale.US)
                    .withZone(ZoneId.systemDefault());

    private final Config config;
    private final EmailSender sender;
    private final Thread thread;
    private volatile boolean running = true;

    public EmailNotifier(Config config) {
        this.config = config;
        this.sender = config.smtpHost.isEmpty()
                ? new ConsoleEmailSender()
                : new SmtpEmailSender(config);
        this.thread = new Thread(this, "email-notifier");
        this.thread.setDaemon(true);
    }

    public static EmailNotifier startDefault() {
        EmailNotifier notifier = new EmailNotifier(Config.fromEnv());
        notifier.start();
        return notifier;
    }

    public void start() {
        thread.start();
    }

    public void stop() {
        running = false;
        thread.interrupt();
    }

    @Override
    public void run() {
        while (running) {
            try {
                AlertEvent first = AlertBus.take();
                List<AlertEvent> batch = new ArrayList<>();
                batch.add(first);

                long deadline = System.currentTimeMillis() + config.batchWindowMs;
                while (true) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) break;
                    AlertEvent next = AlertBus.poll(remaining);
                    if (next == null) break;
                    batch.add(next);
                }

                sendBatch(batch);
            } catch (InterruptedException e) {
                if (!running) return;
            } catch (Exception e) {
                System.err.println("[EmailNotifier] Error: " + e.getMessage());
            }
        }
    }

    private void sendBatch(List<AlertEvent> batch) {
        if (batch.isEmpty()) return;

        String subject = config.subjectPrefix + " " + batch.size() + " change(s)";
        StringBuilder body = new StringBuilder();
        body.append("FIM consolidated alerts\n\n");

        for (AlertEvent e : batch) {
            body.append(TIME_FMT.format(e.timestamp))
                    .append("  ")
                    .append(e.type)
                    .append("  ")
                    .append(e.path);
            if (e.oldPath != null && !e.oldPath.isEmpty()) {
                body.append("  (from: ").append(e.oldPath).append(")");
            }
            body.append("\n");
        }

        List<Attachment> attachments = new ArrayList<>();
        for (AlertEvent e : batch) {
            if (!shouldAttach(e)) continue;
            File f = new File(e.absolutePath);
            if (!f.exists() || !f.isFile()) continue;
            if (f.length() > config.attachMaxBytes) continue;
            attachments.add(new Attachment(f.getName(), f));
        }

        sender.send(
                config.from,
                config.toList,
                subject,
                body.toString(),
                attachments
        );
    }

    private boolean shouldAttach(AlertEvent e) {
        if (e.isDirectory) return false;
        return e.type == AlertEvent.Type.NEW_FILE
                || e.type == AlertEvent.Type.MODIFIED
                || e.type == AlertEvent.Type.RESTORED;
    }

    public static final class Config {
        public final String smtpHost;
        public final int smtpPort;
        public final boolean startTls;
        public final String username;
        public final String password;
        public final String from;
        public final List<String> toList;
        public final String subjectPrefix;
        public final long batchWindowMs;
        public final long attachMaxBytes;

        private Config(
                String smtpHost,
                int smtpPort,
                boolean startTls,
                String username,
                String password,
                String from,
                List<String> toList,
                String subjectPrefix,
                long batchWindowMs,
                long attachMaxBytes
        ) {
            this.smtpHost = smtpHost;
            this.smtpPort = smtpPort;
            this.startTls = startTls;
            this.username = username;
            this.password = password;
            this.from = from;
            this.toList = toList;
            this.subjectPrefix = subjectPrefix;
            this.batchWindowMs = batchWindowMs;
            this.attachMaxBytes = attachMaxBytes;
        }

        public static Config fromEnv() {
            String host = env("FIM_SMTP_HOST", "");
            int port = envInt("FIM_SMTP_PORT", 25);
            boolean startTls = envBool("FIM_SMTP_STARTTLS", false);
            String user = env("FIM_SMTP_USER", "");
            String pass = env("FIM_SMTP_PASS", "");
            String from = env("FIM_MAIL_FROM", "fim@localhost");
            String to = env("FIM_MAIL_TO", "");
            String subject = env("FIM_MAIL_SUBJECT", "[FIM]");
            long batchSec = envLong("FIM_BATCH_SEC", 45);
            long attachMax = envLong("FIM_ATTACH_MAX_BYTES", 512 * 1024);

            List<String> toList = new ArrayList<>();
            if (!to.trim().isEmpty()) {
                for (String s : to.split(",")) {
                    if (!s.trim().isEmpty()) toList.add(s.trim());
                }
            }

            return new Config(
                    host,
                    port,
                    startTls,
                    user,
                    pass,
                    from,
                    toList,
                    subject,
                    TimeUnit.SECONDS.toMillis(batchSec),
                    attachMax
            );
        }

        private static String env(String key, String def) {
            String v = System.getenv(key);
            return v == null ? def : v;
        }

        private static int envInt(String key, int def) {
            try { return Integer.parseInt(env(key, String.valueOf(def))); }
            catch (Exception e) { return def; }
        }

        private static long envLong(String key, long def) {
            try { return Long.parseLong(env(key, String.valueOf(def))); }
            catch (Exception e) { return def; }
        }

        private static boolean envBool(String key, boolean def) {
            String v = env(key, String.valueOf(def));
            return "true".equalsIgnoreCase(v) || "1".equals(v);
        }
    }

    private interface EmailSender {
        void send(String from, List<String> to, String subject, String body, List<Attachment> attachments);
    }

    private static final class ConsoleEmailSender implements EmailSender {
        @Override
        public void send(String from, List<String> to, String subject, String body, List<Attachment> attachments) {
            System.out.println("[EmailNotifier] SMTP not configured. Printing email instead:");
            System.out.println("From: " + from);
            System.out.println("To: " + to);
            System.out.println("Subject: " + subject);
            System.out.println(body);
            if (!attachments.isEmpty()) {
                System.out.println("Attachments: " + attachments.size());
            }
        }
    }

    private static final class SmtpEmailSender implements EmailSender {
        private final Config config;

        private SmtpEmailSender(Config config) {
            this.config = config;
        }

        @Override
        public void send(String from, List<String> to, String subject, String body, List<Attachment> attachments) {
            if (to.isEmpty()) return;
            try {
                Session session = buildSession();
                MimeMessage message = new MimeMessage(session);

                message.setFrom(new InternetAddress(from));
                for (String r : to) {
                    message.addRecipient(Message.RecipientType.TO, new InternetAddress(r));
                }
                message.setSubject(subject, StandardCharsets.UTF_8.name());

                Multipart multipart = new MimeMultipart();

                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText(body, StandardCharsets.UTF_8.name());
                multipart.addBodyPart(textPart);

                for (Attachment a : attachments) {
                    MimeBodyPart attachmentPart = new MimeBodyPart();
                    DataSource source = new FileDataSource(a.file);
                    attachmentPart.setDataHandler(new DataHandler(source));
                    attachmentPart.setFileName(a.name);
                    multipart.addBodyPart(attachmentPart);
                }

                message.setContent(multipart);
                Transport.send(message);
            } catch (MessagingException e) {
                System.err.println("[EmailNotifier] SMTP send failed: " + e.getMessage());
            }
        }

        private Session buildSession() {
            Properties props = new Properties();
            props.put("mail.smtp.host", config.smtpHost);
            props.put("mail.smtp.port", String.valueOf(config.smtpPort));
            props.put("mail.smtp.auth", String.valueOf(!config.username.isEmpty()));
            props.put("mail.smtp.starttls.enable", String.valueOf(config.startTls));

            if (config.username.isEmpty()) {
                return Session.getInstance(props);
            }

            return Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.username, config.password);
                }
            });
        }
    }

    private static final class Attachment {
        final String name;
        final File file;

        Attachment(String name, File file) {
            this.name = name;
            this.file = file;
        }
    }
}
