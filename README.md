# POS Tax VAT System

A Point of Sale (POS) and Tax/VAT Management System for development and testing, built with Spring Boot 3.x and implementing transaction processing, VAT calculations, a fiscal-authority fixture workflow, and audit-event recording.

## Table of Contents

1. [Project Overview](#project-overview)
2. [Technology Stack](#technology-stack)
3. [Database Schema](#database-schema)
4. [API Documentation](#api-documentation)
5. [Transaction State Machine](#transaction-state-machine)
6. [Security & Compliance](#security--compliance)
7. [Setup & Installation](#setup--installation)
8. [Configuration](#configuration)

## Project Overview

The POS Tax VAT System is a production-ready transaction processing platform that handles:

- **Purchase Transactions**: Complete end-to-end checkout processing with multi-tender support
- **Pre-Authorizations**: Card hold management for hospitality and rental industries
- **Refunds**: Proportional tax calculations for partial and full refunds
- **Reversals**: Same-day transaction voiding before settlement
- **VAT Calculation**: Line-by-line tax computation with service fee integration
- **Fiscal receipt workflow**: Profile-based authority adapter with a JSON fixture response for testing; live authority submission is not configured
- **Audit Logging**: Transaction and payment audit-event recording

### Key Features

- **Integer-based Currency Storage**: All monetary values stored in cents to prevent floating-point rounding errors
- **Idempotency Protection**: Duplicate request prevention using unique idempotency keys
- **Multi-Tender Support**: Split payments across cash, card, mobile money, and other methods
- **Proportional Tax Refunds**: Accurate VAT calculations for partial returns
- **State Machine Enforcement**: Strict transaction lifecycle management
- **Audit Trail**: Complete history of all transaction modifications
- **Pre-Auth Expiry**: Automated background processing of expired holds

## Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Java Version**: 17
- **Database**: PostgreSQL (for JSONB support)
- **Build Tool**: Maven
- **Security**: Spring Security
- **Validation**: Jakarta Bean Validation
- **ORM**: Hibernate/JPA
- **Lombok**: Boilerplate code reduction

## Database Schema

### Core Tables

#### pos_transactions
Main transaction ledger storing all financial transaction records.

**Key Fields:**
- `transaction_id`: Unique transaction identifier
- `status`: Transaction state (PENDING, AUTHORIZED, CAPTURED, COMPLETED, etc.)
- `transaction_type`: PURCHASE, PRE_AUTH, REFUND, REVERSAL
- `cash_register_id`: Linked physical terminal
- `cashier_id**: Operator who processed the transaction
- `idempotency_key`: Duplicate prevention token
- `gross_amount`: Total before tax (in cents)
- `vat_amount`: Tax amount (in cents)
- `service_fee_amount`: Service fee (in cents)
- `net_amount`: Final amount (in cents)
- `fiscal_signature`: Authority receipt signature when provided; fixture values are test-only
- `preauth_hold_amount`: Pre-authorization hold amount
- `preauth_expires_at`: Pre-auth expiry timestamp
- `refundable_amount`: Amount available for refund
- `refunded_amount`: Total amount already refunded

#### pos_transaction_line_items
Individual line items for each transaction.

**Key Fields:**
- `transaction_id`: Foreign key to pos_transactions
- `item_code`: Product SKU
- `item_name`: Product description
- `unit_price`: Price per unit (in cents)
- `quantity`: Number of units
- `line_gross_amount`: Line total before tax
- `line_vat_amount`: Line tax amount
- `line_service_fee_amount`: Line service fee
- `line_net_amount`: Line final amount
- `discount_amount`: Line discount
- `vat_exempt`: Tax exemption flag
- `vat_category`: Tax category code

#### pos_payment_tenders
Payment method breakdown for each transaction.

**Key Fields:**
- `transaction_id`: Foreign key to pos_transactions
- `payment_method`: CASH, CARD, MOBILE_MONEY, QR, etc.
- `payment_provider`: Payment gateway (CBE, Telebirr, etc.)
- `amount_tendered`: Amount presented by customer
- `amount_applied`: Amount actually applied to transaction
- `cash_change`: Change given for cash payments
- `card_last_four`: Last 4 digits of card (PCI-DSS compliant)
- `card_brand`: Card network (VISA, Mastercard, etc.)
- `auth_code`: Bank authorization code
- `rrn`: Retrieval Reference Number
- `status`: PENDING, AUTHORIZED, CAPTURED, FAILED, QUEUED

#### pos_audit_log
Immutable audit trail for all transaction modifications.

**Key Fields:**
- `transaction_id`: Foreign key to pos_transactions
- `actor_id`: User who performed the action
- `actor_role`: User role (CASHIER, MANAGER, ADMIN)
- `action`: Action performed (PURCHASE, REFUND, REVERSAL, etc.)
- `from_status`: Previous transaction status
- `to_status`: New transaction status
- `payload_details`: JSONB field for additional context
- `ip_address`: Source IP address
- `reason`: Reason for modification

#### vat_rates
VAT rate configuration for product categories.

**Key Fields:**
- `category_code`: Unique category identifier
- `category_name`: Category description
- `rate`: VAT rate as decimal (e.g., 0.15 for 15%)
- `rate_type`: STANDARD, REDUCED, ZERO_RATED, EXEMPT
- `active`: Whether rate is currently in use
- `effective_from`: Rate start date
- `effective_to`: Rate end date

#### tax_transactions
Fiscal-submission history records. In the fixture profile these are simulated responses, not tax-authority submissions.

**Key Fields:**
- `tax_transaction_id`: Unique tax transaction identifier
- `pos_transaction_id`: Link to POS transaction
- `fiscal_signature`: Authority signature when supplied; fixture values are test-only
- `fiscal_reference`: Authority reference when supplied; fixture values are test-only
- `tin`: Tax Identification Number
- `submission_status`: PENDING, SUBMITTED, ACCEPTED, REJECTED
- `is_credit_note`: Whether this is a credit note
- `original_transaction_id`: Original transaction for credit notes

## API Documentation

### Base URL
```
http://localhost:8080/api/v1/pos/transactions
```

### Endpoints

#### 1. Purchase Transaction

**POST** `/api/v1/pos/transactions/purchase`

Processes a standard purchase transaction with VAT calculation and fiscal receipt generation.

**Request Body:**
```json
{
  "idempotencyKey": "unique-key-12345",
  "cashRegisterId": "8b9f71c4-e1b2-4d2a-ba0f-90e633d45c11",
  "cashierId": "cashier-001",
  "lineItems": [
    {
      "itemCode": "SKU-001",
      "itemName": "Product A",
      "unitPrice": 10000,
      "quantity": 2,
      "discountAmount": 0,
      "vatExempt": false,
      "vatCategory": "STANDARD"
    }
  ],
  "paymentTenders": [
    {
      "paymentMethod": "CARD",
      "paymentProvider": "CBE",
      "amountTendered": 20000
    }
  ],
  "customerName": "John Doe",
  "customerTin": "1234567890"
}
```

**Response (201 Created):**
```json
{
  "transactionId": "TXN-20260929-092500-abc123",
  "status": "COMPLETED",
  "transactionType": "PURCHASE",
  "grossAmount": 20000,
  "vatAmount": 2609,
  "serviceFeeAmount": 400,
  "netAmount": 16991,
  "totalAmountPaid": 20000,
  "fiscalSignature": "SIG-ABC123...",
  "fiscalReference": "FISCAL-XYZ789",
  "createdAt": "2026-09-29T09:25:00",
  "authorizedAt": "2026-09-29T09:25:01",
  "settledAt": "2026-09-29T09:25:02",
  "lineItems": [...],
  "paymentTenders": [...]
}
```

#### 2. Pre-Authorization

**POST** `/api/v1/pos/transactions/preauth`

Creates a hold on customer funds without immediate capture.

**Request Body:**
```json
{
  "idempotencyKey": "preauth-key-12345",
  "cashRegisterId": "8b9f71c4-e1b2-4d2a-ba0f-90e633d45c11",
  "cashierId": "cashier-001",
  "preauthHoldAmount": 100000,
  "paymentTenders": [
    {
      "paymentMethod": "CARD",
      "paymentProvider": "CBE",
      "amountTendered": 100000
    }
  ],
  "customerName": "Abebe Bikila",
  "expiresInHours": 72
}
```

**Response (201 Created):**
```json
{
  "transactionId": "TXN-20260929-093000-def456",
  "status": "AUTHORIZED",
  "transactionType": "PRE_AUTH",
  "grossAmount": 100000,
  "vatAmount": 0,
  "serviceFeeAmount": 0,
  "netAmount": 100000,
  "totalAmountPaid": 100000,
  "createdAt": "2026-09-29T09:30:00",
  "authorizedAt": "2026-09-29T09:30:01",
  "lineItems": [],
  "paymentTenders": [...]
}
```

#### 3. Capture Pre-Authorization

**POST** `/api/v1/pos/transactions/{id}/capture`

Converts a pre-authorization hold into an actual charge.

**Request Body:**
```json
{
  "captureAmount": 85000
}
```

**Response (200 OK):**
```json
{
  "transactionId": "TXN-20260929-093000-def456",
  "status": "CAPTURED",
  "transactionType": "PRE_AUTH",
  "netAmount": 85000,
  "capturedAt": "2026-09-29T10:00:00",
  ...
}
```

#### 4. Refund Transaction

**POST** `/api/v1/pos/transactions/{id}/refund`

Processes a refund with proportional VAT calculation.

**Request Body:**
```json
{
  "idempotencyKey": "refund-key-12345",
  "refundAmount": 5000,
  "lineItemIds": ["item-1", "item-2"],
  "reason": "Customer return"
}
```

**Response (200 OK):**
```json
{
  "transactionId": "TXN-20260929-100500-ghi789",
  "status": "REFUNDED",
  "transactionType": "REFUND",
  "grossAmount": 4348,
  "vatAmount": 652,
  "netAmount": 5000,
  "settledAt": "2026-09-29T10:05:00",
  ...
}
```

#### 5. Reversal Transaction

**POST** `/api/v1/pos/transactions/{id}/reverse`

Voids a transaction before settlement (same-day only).

**Request Body:**
```json
{
  "idempotencyKey": "reverse-key-12345",
  "reason": "Clerk error"
}
```

**Response (200 OK):**
```json
{
  "transactionId": "TXN-20260929-101000-jkl012",
  "status": "REVERSED",
  "transactionType": "REVERSAL",
  "settledAt": "2026-09-29T10:10:00",
  ...
}
```

#### 6. Get Transaction

**GET** `/api/v1/pos/transactions/{id}`

Retrieves transaction details by ID.

**Response (200 OK):**
```json
{
  "transactionId": "TXN-20260929-092500-abc123",
  "status": "COMPLETED",
  "transactionType": "PURCHASE",
  ...
}
```

#### 7. Cancel Transaction

**POST** `/api/v1/pos/transactions/{id}/cancel`

Cancels a pending transaction before authorization.

**Response (200 OK)**

## Transaction State Machine

### Purchase Lifecycle
```
PENDING → AUTHORIZED → CAPTURED → COMPLETED
                                    ↓
                              PARTIALLY_REFUNDED → REFUNDED
```

### Pre-Authorization Lifecycle
```
PENDING → AUTHORIZED → PARTIALLY_CAPTURED → CAPTURED
           ↓              ↓                    ↓
         EXPIRED        CAPTURED       PARTIALLY_REFUNDED → REFUNDED
           ↓                                    ↓
        CANCELLED                           REFUNDED
```

### Refund Lifecycle
```
COMPLETED → REFUND_PENDING → PARTIALLY_REFUNDED → REFUNDED
              ↓                  ↓
         REFUND_FAILED       REFUNDED
```

### Reversal Lifecycle
```
AUTHORIZED/CAPTURED → REVERSAL_PENDING → REVERSED
                         ↓
                   REVERSAL_FAILED
```

## Security & Compliance

### PCI-DSS Compliance
- Card numbers stored with only last 4 digits
- CVV never stored
- TLS 1.2+ encryption for all communications

### Data Privacy
- Customer data masked in logs
- Audit trail for all modifications
- Role-based access control

### Fiscal receipt workflow

The fixture profile uses `src/main/resources/fixtures/fiscal-authority-response.json` to simulate an authority response. The fixture can return accepted, rejected, failed, or queued submission states. Accepted fixture results use `TEST-FISCAL-...` and `TEST-SIGNATURE-...` identifiers; they are not valid authority-issued receipt values. Fiscal submission history is stored separately from the payment transaction, and receipt failures do not roll back a payment that has already completed.

Non-fixture mode currently records an `AUTHORITY_NOT_CONFIGURED` fiscal failure rather than generating an official receipt. A production authority adapter, legally approved document mapping, retry/idempotency behavior, and authority reconciliation must be implemented and verified before statutory use. Refund credit-note treatment must be confirmed for the applicable jurisdiction.

The fiscal-submission record captures the linked POS transaction, currency and financial totals, submission mode/status, response details, and credit-note linkage. A fixture `ACCEPTED` status only means the local simulator returned that result.

### Role-Based Access Control

| Role | Permissions |
|------|-------------|
| Cashier | Process purchases, basic returns |
| Senior Cashier | Pre-auth, voids, higher limits |
| Manager | High-value refunds, overrides |
| Admin | System configuration, full access |

## Setup & Installation

### Prerequisites
- Java 17 or higher
- PostgreSQL 14 or higher
- Maven 3.8+

### Database Setup

1. Create PostgreSQL database:
```sql
CREATE DATABASE pos_tax_vat_db;
```

2. Update `application.properties` with your database credentials:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/pos_tax_vat_db
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### Build and Run

1. Clone the repository:
```bash
git clone <repository-url>
cd POS_Tax_VAT_System
```

2. Build the project:
```bash
mvn clean install
```

3. Run the application:
```bash
mvn spring-boot:run
```

Or run the JAR directly:
```bash
java -jar target/pos-tax-vat-system-1.0.0.jar
```

## Configuration

### Application Properties

Key configuration options in `src/main/resources/application.properties`:

```properties
# Server
server.port=8080

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/pos_tax_vat_db
spring.datasource.username=postgres
spring.datasource.password=postgres

# JWT
jwt.secret=YourSuperSecretKeyForJWTTokenGenerationMustBeLongEnough
jwt.expiration=86400000

# VAT
vat.service.fee.rate=0.02
vat.refund.window.days=90

# Payment Gateway
payment.gateway.timeout.seconds=30
payment.gateway.offline.mode.enabled=false

# Pre-Auth
preauth.default.expiry.hours=72
preauth.expiry.check.interval.minutes=5
```

### Environment Variables

Alternatively, use environment variables:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/pos_tax_vat_db
export DB_USER=postgres
export DB_PASSWORD=postgres
export JWT_SECRET=your-secret-key
export SERVER_PORT=8080
```

## Error Handling

All errors follow a standard format:

```json
{
  "error": {
    "code": "POS_REFUND_AMOUNT_EXCEEDED",
    "message": "Refund amount exceeds remaining refundable balance",
    "details": {
      "requested": 25000,
      "remaining_refundable": 11500,
      "original_transaction": "TXN-20260909-00042"
    },
    "retryable": false
  },
  "timestamp": "2026-09-29T10:00:00"
}
```

### Common Error Codes

- `POS_IDEMPOTENCY_KEY_EXISTS`: Duplicate request
- `POS_INSUFFICIENT_PAYMENT`: Payment amount insufficient
- `POS_EXACT_AMOUNT_REQUIRED`: Non-cash payment must be exact
- `POS_TRANSACTION_NOT_FOUND`: Transaction does not exist
- `POS_INVALID_STATE`: Invalid transaction state for operation
- `POS_PREAUTH_EXPIRED`: Pre-authorization has expired
- `POS_INSUFFICIENT_FUNDS`: Capture amount exceeds hold
- `POS_REFUND_WINDOW_EXPIRED`: Refund time window exceeded
- `POS_REFUND_AMOUNT_EXCEEDED`: Refund exceeds available balance
- `POS_SETTLED_TRANSACTION`: Cannot reverse settled transaction
- `POS_PARTIAL_REFUND_EXISTS`: Cannot reverse with partial refunds

## Testing

### Run Unit Tests
```bash
mvn test
```

### Run Integration Tests
```bash
mvn verify -Pintegration
```

## License

This project is proprietary software. All rights reserved.

## Support

For technical support, contact the development team.
