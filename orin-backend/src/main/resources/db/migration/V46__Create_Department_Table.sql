-- V46: Create sys_department table
CREATE TABLE IF NOT EXISTS sys_department (
    department_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    department_name VARCHAR(100) NOT NULL,
    department_code VARCHAR(50) UNIQUE,
    parent_id BIGINT,
    order_num INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ENABLED',
    leader VARCHAR(50),
    phone VARCHAR(20),
    description VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Insert default departments
INSERT INTO sys_department (department_name, department_code, parent_id, order_num, status, leader, description) VALUES
('技术部', 'DEPT_TECH', NULL, 1, 'ENABLED', 'Tech Lead', '负责技术研发工作'),
('产品部', 'DEPT_PRODUCT', NULL, 2, 'ENABLED', 'Product Manager', '负责产品设计和规划'),
('运营部', 'DEPT_OPERATION', NULL, 3, 'ENABLED', 'Ops Manager', '负责运营和推广'),
('市场部', 'DEPT_MARKET', NULL, 4, 'ENABLED', 'Marketing Lead', '负责市场拓展');