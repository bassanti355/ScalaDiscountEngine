package engine
import engine.main.logWriter

import java.io.{File, FileOutputStream, PrintWriter}
import java.sql.{Date, DriverManager, PreparedStatement}
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate}
import scala.io.Source


// ========================================================================
//1. Initialize logging and output files
// ========================================================================


object main extends App {
  // Initialize logging
  val logFile: File = new File("src/main/resources/rules_engine.log")
  val logWriter = new PrintWriter(new FileOutputStream(logFile, true))

  // Initialize output files
  val outputCSV = new File("src/main/resources/orders_with_discounts.csv")
  val csvWriter = new PrintWriter(outputCSV)


  // Database configuration
  val url = "jdbc:postgresql://localhost:5432/discounts_db"
  val username = "postgres"
  val password = "postgres"


  // Log application start
  log("Application started")

  //
  //  // Create the output CSV file and write the header
  //  writeResultsToCSV(processedOrders, csvWriter)
  //


  // ========================================================================
  //2. read the csv file and skip the header
  // ========================================================================

  val lines = Source.fromFile("src/main/resources/TRX1000.csv").getLines().toList.tail

  // Log successful data read
  //log_event(logWriter, logFile, "info", s"Read ${orders.length} orders from input file")


  // ========================================================================
  // 3. create a case data models
  // ========================================================================


  case class Order(
                    timestamp: String,
                    product_name: String,
                    expiry_date: String,
                    quantity: Int,
                    unit_price: Double,
                    channel: String,
                    payment_method: String,

                  )

  /**
   * ProcessedOrder case class to hold the original order and the discount applied as well as the final price
   *
   * @param original   Original Order object
   * @param discount   Discount percentage applied
   * @param finalPrice Final price after applying the discount
   */

  case class ProcessedOrder(
                             original: Order,
                             discount: Double,
                             finalPrice: Double
                           )
  // ========================================================================
  // 4. load data / create a function to parse the csv file and return a list of orders
  // ========================================================================


  def parse_orders(line: String): Order = {
    val fields = line.split(",")
    Order(
      timestamp = fields(0),
      product_name = fields(1),
      expiry_date = fields(2),
      quantity = fields(3).toInt,
      unit_price = fields(4).toDouble,
      channel = fields(5),
      payment_method = fields(6)
    )
  }

  // Parse the CSV lines into Order objects
  val orders = lines.map(parse_orders)



  // ====================================================
  // 5. Implement the Qualifiers and Discount Calculators
  // ====================================================


  // Define all qualifier functions (checking if the order meets certain criteria)

  /**
   * Check if quantity is greater than 5
   * - Quantity is an integer
   *
   * @param orders
   * @return boolean
   *
   */
  def more_than_5_qualifier(orders: Order): Boolean = {
    val quantity = orders.quantity
    quantity > 5
  }


  /**
   * Check if product name contains "Cheese" or "Wine"
   * - Product name is case-insensitive
   *
   * @param orders
   * @return boolean
   *
   */

  def cheese_and_wine_qualifier(orders: Order): Boolean = {
    val productName = orders.product_name.toLowerCase
    if (productName.contains("Cheese")) true
    else if (productName.contains("Wine")) true
    else false
  }


  /**
   * Check if order date is March 23rd
   * - Order date is in the format "yyyy-MM-dd"
   *
   * @param orders
   * @return boolean
   *
   */

  def products_sold_23_march_qualifier(orders: Order): Boolean = {
    //orders.timestamp.contains("2023-03-23")
    val date = new SimpleDateFormat("yyyy-MM-dd").parse(orders.timestamp.substring(0, 10))
    val targetDate = new SimpleDateFormat("yyyy-MM-dd").parse("2023-03-23")
    date == targetDate

  }


  /**
   * Check if the order is less than 30 days from expiry date
   * - Order date is in the format "yyyy-MM-dd"
   * - Expiry date is in the format "yyyy-MM-dd"
   *
   * @param orders
   * @return boolean
   */

  def less_than_30_qualifier_using_days_between(orders: Order): Boolean = {
    val orderDate = LocalDate.parse(orders.timestamp.substring(0, 10), DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val expiryDate = LocalDate.parse(orders.expiry_date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(orderDate, expiryDate)
    daysBetween < 30
  }

  /**
   * Check if the order is made through the app
   *
   * @param orders
   * @return
   */

  def app_usage_qualifier(orders: Order): Boolean = {

    orders.channel == "App"
  }


  /**
   * Check if the payment method is Visa
   *
   * @param orders
   * @return boolean
   */
  def visa_card_qualifier(orders: Order): Boolean = {
    // Check if payment method is "Visa"
    orders.payment_method == "Visa"
  }


  // Define all discount calculation functions

  /**
   * Calculate discount based on quantity tiers
   * - 6–9 units -> 5 % discount
   * - 10 - 14 units -> 7 % discount
   * - More than 15 -> 10 % discount
   *
   * @param orders
   * @return discount percentage
   *
   */
  def get_more_than_5_discount(orders: Order): Double = {


    val quantity = orders.quantity
    if (quantity >= 6 && quantity <= 9) 0.05
    else if (quantity >= 10 && quantity <= 14) 0.07
    else if (quantity > 15) 0.10
    else 0.0
  }


  /**
   * Calculate discount based on product name
   * - Cheese -> 10 % discount
   * - Wine -> 5 % discount
   *
   * @param orders
   * @return discount percentage
   *
   */

  def get_cheese_and_wine_discount(orders: Order): Double = {
    val productName = orders.product_name.toLowerCase
    if (productName.contains("cheese")) 0.10
    else if (productName.contains("wine")) 0.05
    else 0.0

  }


  /**
   * Calculate discount based on order date
   * - March 23rd -> 50 % discount
   *
   * @param orders
   * @return discount percentage
   *
   */
  def get_products_sold_23_march_discount(orders: Order): Double = {
    //happy mothers day :)
    0.50
  }


  /**
   *
   * Calculate discount based on days remaining (1% per day under 30)
   * - 29 days remaining -> 1 % discount
   * - 28 days remaining -> 2 % discount // ..etc
   * - 1 day remaining -> 29 % discount
   *
   * @param orders
   * @return discount percentage
   */

  def get_less_than_30_qualifier_discount(orders: Order): Double = {
    val orderDate = LocalDate.parse(orders.timestamp.substring(0, 10))
    val expiryDate = LocalDate.parse(orders.expiry_date)
    val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(orderDate, expiryDate)

    if (daysRemaining >= 30) 0.0
    else (30 - daysRemaining) * 0.01

  }

  /** Calculate discount based on quantity tiers (rounded up to nearest 5)
   * quantity rounded up to the nearest multiple of 5.
   * - Ex: quantity: 1, 2, 3, 4, 5 ‐> discount 5%
   * - EX : quantity: 6, 7, 8, 9, 10 ‐> discount 10%
   *
   * @param orders Order object
   * @return Discount percentage
   *
   */

  def get_app_usage_discount(orders: Order): Double = {

    val quantity = if orders.quantity >= 15 then 15 else orders.quantity
    val roundedQuantity = math.ceil(quantity / 5) * 5
    roundedQuantity / 100.0

  }

  /**
   * Calculate discount based on payment method
   * - Visa -> 5 % discount
   *
   * @param orders
   * @return discount percentage
   *
   */

  def get_visa_card_discount(orders: Order): Double = {
    // Flat 5% discount
    0.05
  }






  // ==============================================
  // 6. Main Processing Function
  // ==============================================

  /**
   *
   * Process the order with applicable discounts
   *  - Step 1: Find all applicable discounts
   *  - Step 2: Calculate final discount according to business rules
   *  - Step 3: Compute final price
   *
   * @param order Order object
   * @return ProcessedOrder
   */

  def processOrderWithDiscounts(order: Order): ProcessedOrder = {
    val discountRules = get_list_of_rules()

    // Step 1: Find all applicable discounts
    val TopTwoDiscounts = discountRules.filter(_._1(order)).map(_._2(order)).sorted.reverse.take(2)

    // Step 2: Calculate final discount according to business rules
    val disounts = if (TopTwoDiscounts.nonEmpty) TopTwoDiscounts.sum / TopTwoDiscounts.length else 0.0

    // Step 3: Compute final price
    val finalPrice = calculateFinalPrice(order, disounts)


    // Step 4 : Return enriched order
    ProcessedOrder(
      original = order,
      discount = disounts,
      finalPrice = finalPrice
    )
  }


  // ==============================================
  // 7. Helper Functions
  // ==============================================


  /** Returns a list of discount rules and their corresponding calculators.
   * Each pair consists of:
   * - A qualifier function that determines if an order is eligible for a discount
   * - A calculator function that computes the discount amount
   *
   * @return List of (qualifier, calculator) pairs
   *
   */


  // Type aliases for better readability
  type Qualifier = Order => Boolean
  type DiscountCalculator = Order => Double


  def get_list_of_rules(): List[(Qualifier, DiscountCalculator)] = {
    List(
      (cheese_and_wine_qualifier, get_cheese_and_wine_discount),
      (more_than_5_qualifier, get_more_than_5_discount),
      (products_sold_23_march_qualifier, get_products_sold_23_march_discount),
      (less_than_30_qualifier_using_days_between, get_less_than_30_qualifier_discount),
      (app_usage_qualifier, get_app_usage_discount),
      (visa_card_qualifier, get_visa_card_discount)
    )
  }


  /**
   * Calculate the final price after applying the discount.
   * - Formula: Final Price = Quantity * Unit Price * (1 - Discount)
   *
   * @param order    Order object
   * @param discount Discount percentage
   * @return Final price after discount
   */

  def calculateFinalPrice(order: Order, discount: Double): Double = {
    order.quantity * order.unit_price * (1 - discount)
  }




  // ==============================================
  // 8.Output Functions
  // ==============================================


  /**
   * Write the processed orders to a CSV file.
   *
   * @param processedOrders
   * @param writer
   */

  def writeResultsToCSV(processedOrders: List[ProcessedOrder], writer: PrintWriter): Unit = {
    writer.println("timestamp,product_name,expiry_date,quantity,unit_price,channel,payment_method,discount,final_price")
    processedOrders.foreach { p =>
      val o = p.original
      writer.println(s"${o.timestamp},${o.product_name},${o.expiry_date},${o.quantity},${o.unit_price},${o.channel},${o.payment_method},${p.discount},${p.finalPrice}")
    }
    writer.close()

  }


  /**
   * Log messages with timestamp
   *
   * @param message The message to log
   */

  def log(message: String): Unit = {
    val timestamp = Instant.now()
    logWriter.write(s"$timestamp - $message\n")
    logWriter.flush()
  }

  /**
   * Write processed orders to database
   *
   * @param processedOrders List of processed orders
   */
  def writeToDatabase(processedOrders: List[ProcessedOrder]): Unit = {
    log("Starting database write operation")
    var connection: java.sql.Connection = null
    var statement: PreparedStatement = null

    try {
      connection = DriverManager.getConnection(url, username, password)
      val sql =
        """
        INSERT INTO orders
        (order_date, product_name, expiry_date, quantity, unit_price,
         channel, payment_method, discount, final_price)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      """

      statement = connection.prepareStatement(sql)

      processedOrders.foreach { order =>
        val o = order.original
        statement.setDate(1, Date.valueOf(LocalDate.parse(o.timestamp.substring(0, 10))))
        statement.setString(2, o.product_name)
        statement.setDate(3, Date.valueOf(LocalDate.parse(o.expiry_date)))
        statement.setInt(4, o.quantity)
        statement.setDouble(5, o.unit_price)
        statement.setString(6, o.channel)
        statement.setString(7, o.payment_method)
        statement.setDouble(8, order.discount)
        statement.setDouble(9, order.finalPrice)
        statement.addBatch()
      }

      val results = statement.executeBatch()
      log(s"Successfully wrote ${results.sum} records to database")

      //    } catch {
      //      case e: Exception =>
      //        log(s"Database write failed: ${e.getMessage}")
      //        e.printStackTrace()
    } finally {
      if (statement != null) statement.close()
      if (connection != null) connection.close()
    }
  }

  // ==============================================
  // 8. Final Pipeline Execution
  // ==============================================

  try {
    log("Starting order processing")

    val processedOrders = orders.map(processOrderWithDiscounts)

    log("Writing results to CSV")
    writeResultsToCSV(processedOrders, csvWriter)

    log("Writing results to database")
    writeToDatabase(processedOrders)

    // ✅ Only log this if all previous steps succeed
    log("Processing completed successfully")

  } catch {
    case e: Exception =>
      log(s"Processing failed: ${e.getMessage}")
      e.printStackTrace()
  } finally {
    csvWriter.close()
    logWriter.close()
    log("Resources cleaned up - application shutting down")
  }

}