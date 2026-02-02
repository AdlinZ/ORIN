-- Resize target_url to LONGTEXT to support base64 images
ALTER TABLE multimodal_tasks MODIFY target_url LONGTEXT;
