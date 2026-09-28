INSERT INTO products
(created_at, updated_at, status, description, name, price, stock)
VALUES
    (CURRENT_TIMESTAMP(6),
     CURRENT_TIMESTAMP(6),
     'ACTIVE',
     'Sample product for catalogue verification',
     'Test Product',
     999.99,
     10);