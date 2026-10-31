-- src/main/resources/db/migration/V6__create_purchase_transaction.sql

CREATE TABLE purchase_transaction (
                                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                      user_id BIGINT NOT NULL,
                                      original_transaction_id VARCHAR(255) NOT NULL UNIQUE,
                                      transaction_id VARCHAR(255) NOT NULL UNIQUE,
                                      product_id VARCHAR(255) NOT NULL,
                                      product_type VARCHAR(50) NOT NULL,
                                      purchase_date DATETIME NOT NULL,
                                      expires_date DATETIME NULL,
                                      status VARCHAR(50) NOT NULL,
                                      environment VARCHAR(20) NOT NULL,
                                      created_at DATETIME NOT NULL,
                                      updated_at DATETIME NOT NULL,
                                      INDEX idx_purchase_user_id (user_id)
);