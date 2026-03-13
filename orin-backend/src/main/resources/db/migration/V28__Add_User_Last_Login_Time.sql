-- V25__Add_User_Last_Login_Time.sql
ALTER TABLE sys_user ADD COLUMN last_login_time DATETIME DEFAULT NULL;
