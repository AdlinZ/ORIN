-- Add MailerSend API key and mailer type fields
ALTER TABLE sys_mail_config ADD COLUMN api_key VARCHAR(200);
ALTER TABLE sys_mail_config ADD COLUMN mailer_type VARCHAR(20) DEFAULT 'smtp';
