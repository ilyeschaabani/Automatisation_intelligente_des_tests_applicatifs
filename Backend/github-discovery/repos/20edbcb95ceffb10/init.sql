-- Initialize the database for MicroService application
CREATE DATABASE IF NOT EXISTS codingFactory CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Switch to the new database
USE codingFactory;

-- Create application user with limited privileges (more secure than using root)
CREATE USER IF NOT EXISTS 'appuser'@'%' IDENTIFIED BY 'apppass';
GRANT ALL PRIVILEGES ON codingFactory.* TO 'appuser'@'%';
FLUSH PRIVILEGES;

-- Create any initial tables if needed
-- CREATE TABLE IF NOT EXISTS resources (
--     id BIGINT AUTO_INCREMENT PRIMARY KEY,
--     name VARCHAR(255) NOT NULL,
--     description TEXT,
--     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- System tables maintenance (helps prevent the "wrong structure" errors)
-- This is automatically handled by MySQL 8.0+ on first startup