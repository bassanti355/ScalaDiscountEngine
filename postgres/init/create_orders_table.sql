CREATE TABLE IF NOT EXISTS orders (
    order_id SERIAL PRIMARY KEY,
    order_date DATE,
    product_name TEXT,
    expiry_date DATE,
    quantity INTEGER,
    unit_price DOUBLE PRECISION,
    channel TEXT,
    payment_method TEXT,
    discount DOUBLE PRECISION,
    final_price DOUBLE PRECISION
);
