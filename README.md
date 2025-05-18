## Retail Discount Rule Engine - Comprehensive Documentation

### Overview

This Scala-based retail discount engine processes product transactions from CSV files, applies complex discount rules, and stores the final results in a PostgreSQL database. It features a modular architecture with clean separation of data ingestion, business logic, and storage. Extensive logging ensures operational transparency.

### System Architecture

#### Core Components

* **Data Pipeline**:

  * CSV File Reader (`TRX1000.csv`)
  * Product Data Mapper
  * Discount Rule Engine
  * Database Writer (PostgreSQL)
  * File-based Logging System

* **Business Logic**:

  * Product Qualification Rules
  * Discount Calculation Rules
  * Final Price Computation
  * Rule Prioritization and Combination

* **Infrastructure**:

  * PostgreSQL Database Container
  * JDBC Database Connectivity
  * Comprehensive Logging Mechanism

### Database Configuration

#### PostgreSQL Container Setup (`docker-compose.yml`)

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15
    container_name: discounts_postgres
    restart: unless-stopped
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: discounts_db
    ports:
      - "5432:5432"
    volumes:
      - ./pgdata:/var/lib/postgresql/data
      - ./init:/docker-entrypoint-initdb.d

volumes:
  pgdata:
```

#### Database Schema

```sql
CREATE TABLE product_discounts (
  id SERIAL PRIMARY KEY,
  transaction_date DATE,
  product_name VARCHAR(255),
  expiry_date DATE,
  quantity INTEGER,
  unit_price DECIMAL(10,2),
  channel VARCHAR(50),
  payment_method VARCHAR(50),
  discount DECIMAL(5,4),
  final_price DECIMAL(10,4)
);
```

### Data Model (Scala)

```scala
case class Product(
  transaction_date: LocalDate,
  product_name: String,
  expiry_date: LocalDate,
  quantity: Int,
  unit_price: Double,
  channel: String,
  payment_method: String
)

case class ProductWithDiscount(
  product: Product,
  discount: Double,
  final_price: Double
)
```

### Outputs

* **CSV File**:

  * Location: `src/main/resources/orders_with_discounts.csv`
  * Contains all processed transactions with discounts

* **Database**:

  * Table: `product_discounts`
  * Connection: `jdbc:postgresql://localhost:5432/discounts_db`
  * Credentials: `user=postgres`, `password=postgres`

* **Log File**:

  * Location: `src/main/resources/rules_engine.log`
  * Contains timestamps, discount logic evaluations, and error tracking

### Discount Rules

* **Quantity-Based**:

  * 6–9 units: 5%
  * 10–14 units: 7%
  * 15+ units: 10%

* **Product Type**:

  * Cheese: 10%
  * Wine: 5%

* **Special Date**:

  * March 23rd: 50%

* **Expiry Proximity**:

  * 1% discount per day if expiry is within 30 days

* **App Usage**:

  * Tiered discount based on order quantity via App

* **Payment Method**:

  * Visa: 5%

* **Combination Rule**:

  * Average of the two highest qualifying discounts

### Project Structure

```
src/
├── main/
│   ├── resources/
│   │   ├── TRX1000.csv                # Input
│   │   ├── orders_with_discounts.csv  # Output
│   │   └── rules_engine.log           # Logs
│   └── scala/
│       └── engine/
│           └── main.scala             # Application entry point
build.sbt                              # Build configuration
postgres/                              # Dedicated PostgreSQL configuration
├── docker-compose.yaml                # Standalone DB container setup
└── init/
    └── create_orders_table.sql        # SQL schema initialization
```

### Dependencies

* **Scala**: 3.3.6
* **Java**: JDK 21+
* **Libraries**:

  * PostgreSQL JDBC Driver: 42.7.4

#### `build.sbt`

```scala
ThisBuild / version := "1.0.0"
ThisBuild / scalaVersion := "3.3.6"

libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "42.7.4",
  "joda-time" % "joda-time" % "2.14.0"
)
```

### Execution Workflow

1. **Start Database**:

```bash
docker-compose up -d
```

2. **Run Application**:

```bash
sbt run
```

3. **Verify Outputs**:

   * Check `rules_engine.log` for logs
   * Check PostgreSQL:

```sql
SELECT * FROM product_discounts LIMIT 10;
```



### Sample Data Flow

**Input Record**:

```
2023-03-23,Cheese - Cheddar,2023-04-15,12,5.99,App,Visa
```

**Processing**:

* March 23rd Discount: 50%
* Cheese Discount: 10%
* Quantity (12): 7%
* App Order: 10%
* Visa Payment: 5%

**Top Two Discounts**: 50% and 10%

**Final Discount**: (50 + 10) / 2 = 30%

**Database Insert**:

```sql
INSERT INTO product_discounts (
  transaction_date, product_name, expiry_date, quantity,
  unit_price, channel, payment_method, discount, final_price
) VALUES (
  '2023-03-23', 'Cheese - Cheddar', '2023-04-15',
  12, 5.99, 'App', 'Visa', 0.30, 50.316
);
```

---

This system provides a robust and extensible solution for handling retail discounts based on dynamic business rules. With clear outputs, strong logging, and database persistence, it ensures data accuracy and auditability across retail environments.
