-- Database-per-service: each service owns its schema; no service reads another service's tables.
-- (One PostgreSQL server here to save resources; in production these would be separate instances/clusters.)
CREATE DATABASE auth_db;
CREATE DATABASE order_db;
CREATE DATABASE inventory_db;
CREATE DATABASE payment_db;
