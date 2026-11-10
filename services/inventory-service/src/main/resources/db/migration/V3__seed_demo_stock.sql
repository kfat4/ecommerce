-- Same IDs as product-service's CatalogSeeder
INSERT INTO inventory_items (product_id, available_quantity)
VALUES ('PRD-1001', 50),
       ('PRD-1002', 200),
       ('PRD-1003', 1000),
       ('PRD-1004', 3),   -- low stock: order 4+ to see the inventory failure path of the saga
       ('PRD-1005', 10);
