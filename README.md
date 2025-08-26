
# Artly - Artwork Management System

## Project Overview

Artly is a comprehensive artwork management system built with Spring Boot, MongoDB, and Neo4j. The application provides a platform for artists to showcase their artwork, users to browse, like, and comment on artwork, and includes features for artwork recommendations, user authentication, and more.

## Setup Instructions

### Prerequisites
- Java 17 or higher
- Maven
- MongoDB
- Neo4j
- Python 3.x (for artwork importer)

### Local Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd JWTSecurity
   ```

2. **Configure the application**
   
   Update the `application.yml` file with your MongoDB and Neo4j credentials:
   ```yaml
   spring:
     data:
       mongodb:
         authentication-database: admin
         database: artly
         uri: your-mongodb-uri
       neo4j:
         uri: your-neo4j-uri
         authentication:
           username: your-username
           password: your-password
   ```

3. **Build the application**
   ```bash
   ./mvnw clean install
   ```

4. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```

5. **Install Python dependencies** (for artwork importer)
   ```bash
   pip install -r requirements.txt
   ```

6. **Run the artwork importer**
   ```bash
   python artwork_importer_improved.py --delay 0 --batch-size 20
   ```

### Docker Setup

1. **Build the Docker image**
   ```bash
   docker build -t artly-app .
   ```

2. **Run the Docker container**
   ```bash
   docker run -p 7040:7040 artly-app
   ```

### Accessing the Application

- **API Endpoint**: http://localhost:7040
- **Swagger UI**: http://localhost:7040/swagger-ui/index.html
- **Production Swagger UI**: https://hammerhead-app-zgpcv.ondigitalocean.app/swagger-ui/index.html

## Flow Diagram

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│             │     │             │     │             │
│    User     │────▶│    Auth     │────▶│  Artwork    │
│             │     │   Service   │     │   Service   │
└─────────────┘     └─────────────┘     └─────────────┘
                          │                    │
                          ▼                    ▼
                    ┌─────────────┐     ┌─────────────┐
                    │             │     │             │
                    │  MongoDB    │     │   Neo4j     │
                    │  Database   │     │  Database   │
                    │             │     │             │
                    └─────────────┘     └─────────────┘
```

## Sequence Diagram

### Authentication Flow

```
┌─────┐          ┌─────────────┐          ┌─────────────┐          ┌─────────────┐
│User │          │SecurityApi  │          │ProfileService│          │JwtUtils     │
└──┬──┘          └──────┬──────┘          └──────┬──────┘          └──────┬──────┘
   │  Login Request     │                        │                        │
   │ ─────────────────> │                        │                        │
   │                    │                        │                        │
   │                    │ Authenticate           │                        │
   │                    │ ─────────────────────> │                        │
   │                    │                        │                        │
   │                    │                        │ Generate JWT Token     │
   │                    │                        │ ─────────────────────> │
   │                    │                        │                        │
   │                    │                        │ Return Token           │
   │                    │                        │ <───────────────────── │
   │                    │                        │                        │
   │  JWT Token         │                        │                        │
   │ <───────────────── │                        │                        │
   │                    │                        │                        │
```

### Artwork Browsing Flow

```
┌─────┐          ┌─────────────┐          ┌─────────────┐          ┌─────────────┐
│User │          │ArtworkApi   │          │ArtworkService│          │Database     │
└──┬──┘          └──────┬──────┘          └──────┬──────┘          └──────┬──────┘
   │  Request Artworks  │                        │                        │
   │ ─────────────────> │                        │                        │
   │                    │                        │                        │
   │                    │ Get Artworks           │                        │
   │                    │ ─────────────────────> │                        │
   │                    │                        │                        │
   │                    │                        │ Query Database         │
   │                    │                        │ ─────────────────────> │
   │                    │                        │                        │
   │                    │                        │ Return Data            │
   │                    │                        │ <───────────────────── │
   │                    │                        │                        │
   │  Artwork Data      │                        │                        │
   │ <───────────────── │                        │                        │
   │                    │                        │                        │
```

## App Structure

```
JWTSecurity/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── basic/
│   │   │           └── JWTSecurity/
│   │   │               ├── artwork_server/
│   │   │               │   ├── api/
│   │   │               │   ├── config/
│   │   │               │   ├── dto/
│   │   │               │   ├── model/
│   │   │               │   ├── repository/
│   │   │               │   └── service/
│   │   │               ├── auth/
│   │   │               │   ├── api/
│   │   │               │   ├── config/
│   │   │               │   ├── model/
│   │   │               │   ├── repository/
│   │   │               │   ├── security/
│   │   │               │   └── service/
│   │   │               ├── shopping_server/
│   │   │               │   ├── collection/
│   │   │               │   ├── controller/
│   │   │               │   ├── repository/
│   │   │               │   └── service/
│   │   │               └── JwtSecurityApplication.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
├── artwork_importer_improved.py
├── Dockerfile
├── pom.xml
└── README.md
```

## Database Schema

### MongoDB Collections

#### Profile Collection
```json
{
  "id": "String",
  "username": "String",
  "password": "String",
  "phoneNumber": "String",
  "roles": ["String"]
}
```

### Neo4j Nodes and Relationships

#### Artwork Node
```
Artwork {
  id: String
  title: String
  status: Status
  artist: String
  storageType: StorageType
  releasedDate: LocalDateTime
  type: ArtType
  medium: String
  description: String
  dimensions: String
  current_location: String
  period_style: String
  art_movement: String
  image_url_compressed: String
  image_url: String
  license_info: String
  source_url: String
}
```

#### Profile
The Profile model represents user accounts in the system and is stored in MongoDB.

```java
@Document(collection = "user")
public class Profile {
    @Id
    private String id;              // Unique identifier for the user
    private String username;        // Username for login
    private String phone;           // User's phone number
    private String password;        // Encrypted password
    private List<Address> userAddress;  // User's shipping addresses
    private List<CartItem> cardItems;   // Items in user's shopping cart
    private List<OrderItem> orderItems; // User's order history
}
```

**Purpose**: Manages user authentication, shopping cart, and order history.

#### Address
Represents a shipping address for orders.

```java
@Document(collection = "address")
public class Address {
    @Id
    private String id;              // Unique identifier
    private String name;            // Recipient name
    private String streetAddress;   // Street address
    private String apartment;       // Apartment/suite number
    private String city;            // City
    private String state;           // State/province
    private String zipCode;         // Postal code
    private String phone;           // Contact phone number
    private Boolean isDefault;      // Whether this is the default address
}
```

**Purpose**: Stores shipping information for order fulfillment.

#### CartItem
Represents an item in a user's shopping cart.

```java
@Document(collection = "CartItem")
public class CartItem {
    @Id
    private String id;              // Unique identifier
    private String title;           // Artwork title
    private String artist;          // Artist name
    private Double price;           // Item price
    private String imageUrl;        // Artwork image URL
    private Integer quantity;       // Quantity in cart
}
```

**Purpose**: Tracks items that users have added to their shopping cart.

#### OrderItem
Represents an item in a completed order.

```java
@Document(collection = "OrderItem")
public class OrderItem {
    private String id;              // Unique identifier
    private String title;           // Artwork title
    private String artist;          // Artist name
    private Double price;           // Item price
    private Integer quantity;       // Quantity ordered
    private String imageUrl;        // Artwork image URL
}
```

**Purpose**: Records purchased items in completed orders.

#### OrderSummary
Represents a complete order with all details.

```java
@Document(collection = "OrderSummary")
public class OrderSummary {
    private List<OrderItem> items;          // Items in the order
    private Double subtotal;                // Order subtotal
    private Double shipping;                // Shipping cost
    private Double tax;                     // Tax amount
    private Double total;                   // Total order cost
    private Address shippingAddress;        // Shipping address
    private PaymentMethod paymentMethod;    // Payment method used
}
```

**Purpose**: Provides a complete summary of an order for processing and record-keeping.

#### PaymentMethod
Represents a payment method used for orders.

```java
@Document(collection = "PaymentMethod")
public class PaymentMethod {
    private String type;            // Payment type (credit card, PayPal, etc.)
    private String lastFourDigits;  // Last four digits of card
    private String expiryDate;      // Card expiry date
}
```

**Purpose**: Stores payment information for processing orders.

### Neo4j Models

#### Artwork
The central entity in the artwork graph database.

```java
@Node("Artwork")
public class Artwork {
    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;                  // Unique identifier
    private String title;               // Artwork title
    private Status status;              // Publication status (DRAFT, PUBLISHED, etc.)
    private String artist;              // Artist name
    private StorageType storageType;    // Storage type (Firebase)
    private LocalDateTime releasedDate; // Release date
    private ArtType type;               // Type (IMAGE, VIDEO, etc.)
    private String medium;              // Medium used (oil, acrylic, etc.)
    private String description;         // Artwork description
    private String dimensions;          // Physical dimensions
    private String current_location;    // Current location of the artwork
    private String period_style;        // Art period or style
    private String art_movement;        // Art movement
    private String image_url_compressed;// Compressed image URL
    private String image_url;           // Full-resolution image URL
    private String license_info;        // Licensing information
    private String source_url;          // Source URL

    // Relationships
    @Relationship(value = "HAS_COMMENT", direction = Relationship.Direction.INCOMING)
    private List<Comment> comments;     // Comments on the artwork
    
    @Relationship(value = "FEATURED_WITH", direction = Relationship.Direction.INCOMING)
    private List<Artist> featuredArtist;// Featured artists
    
    @Relationship(value = "RELEASED_IN")
    private Year year;                  // Release year
    
    @Relationship(value = "BELONGS", direction = Relationship.Direction.INCOMING)
    private Gallery gallery;            // Gallery containing the artwork
}
```

**Purpose**: Central entity representing artworks with detailed metadata and relationships to artists, comments, galleries, etc.

#### Artist
Represents an artist who creates artwork.

```java
@Node("Artist")
public class Artist {
    @Id
    private String id;              // Unique identifier
    private String name;            // Artist name
    private String birth_date;      // Birth date
    private String death_date;      // Death date (if applicable)
    private String nationality;     // Nationality
    private String notable_works;   // Notable works
    private String art_movement;    // Art movement
    private String education;       // Educational background
    private String awards;          // Awards received
    private String image_url;       // Artist image URL
    private String wikipedia_url;   // Wikipedia URL
    private String description;     // Artist biography

    // Relationships
    @Relationship(value = "BELONGS_TO_GENRE")
    private Genre genre;            // Primary genre
    
    @Relationship("CREATED")
    private List<ArtistRelationship> artworks;  // Created artworks
    
    @Relationship("CREATED")
    private List<GalleryRelationship> galleries; // Created galleries
}
```

**Purpose**: Represents artists with biographical information and relationships to their artworks and galleries.

#### User
Represents a user in the Neo4j database (connected to MongoDB Profile).

```java
@Node("User")
public class User {
    @Id
    private String id;              // Unique identifier (matches MongoDB Profile ID)
    private String name;            // User's display name
    private String profilePicture;  // Profile picture URL
    private LocalDate dob;          // Date of birth
    private String gender;          // Gender
    private String language;        // Preferred language
    private String countryIso2;     // Country code

    // Relationships
    @Relationship("IS_AN")
    private Artist artist;          // If user is also an artist
    
    @Relationship("LIKES")
    private List<Genre> likedGenres;// Genres the user likes
    
    @Relationship("CREATED")
    private List<Favorites> favorites; // User's favorite collections
    
    @Relationship("LIKES")
    private List<Gallery> galleries;// Galleries the user likes
    
    @Relationship("POSTED_COMMENT")
    private List<Comment> comments; // Comments posted by user
    
    @Relationship("FOLLOWS")
    private List<FollowRelationship> artists; // Artists the user follows
}
```

**Purpose**: Represents users in the graph database, enabling social features like following artists, liking artworks, and creating favorites collections.

#### Comment
Represents a comment on an artwork.

```java
@Node("Comment")
public class Comment {
    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;              // Unique identifier
    private String text;            // Comment text
    private LocalDateTime createdAt;// Creation timestamp
    private LocalDateTime updatedAt;// Last update timestamp
    private Boolean edited;         // Whether the comment has been edited

    // Relationships
    @Relationship(type = "POSTED_COMMENT", direction = Relationship.Direction.INCOMING)
    private User user;              // User who posted the comment
    
    @Relationship(type = "HAS_COMMENT")
    private Artwork artwork;        // Artwork being commented on
}
```

**Purpose**: Enables user interaction through comments on artworks.

#### Gallery
Represents a curated collection of artworks.

```java
@Node("Gallery")
public class Gallery {
    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;              // Unique identifier
    private String description;     // Gallery description
    private String name;            // Gallery name
    private String coverUrl;        // Cover image URL
    private Status status;          // Publication status

    // Relationships
    @Relationship(type = "IS_IN", direction = Relationship.Direction.INCOMING)
    private Set<Artwork> artwork;   // Artworks in the gallery
}
```

**Purpose**: Allows for curated collections of artworks to be displayed together.

#### Favorites
Represents a user's collection of favorite artworks.

```java
@Node("Favorites")
public class Favorites {
    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;              // Unique identifier
    private String title;           // Collection title
    private String description;     // Collection description

    // Relationships
    @Relationship("CREATED")
    private User user;              // User who created the collection
    
    @Relationship("CONTAINS")
    private List<Artwork> artworks; // Artworks in the collection
}
```

**Purpose**: Allows users to create personal collections of favorite artworks.

#### Genre
Represents an art genre or category.

```java
@Node("Genre")
public class Genre {
    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;              // Unique identifier
    private String key;             // Genre key/code
    private String name;            // Genre name

    // Relationships
    @Relationship("BELONGS_TO_GENRE")
    private List<Artwork> artworks; // Artworks in this genre
    
    @Relationship("BELONGS_TO_GENRE")
    private List<Artist> artists;   // Artists working in this genre
}
```

**Purpose**: Categorizes artworks and artists by genre, enabling genre-based browsing and recommendations.

#### Year
Represents a year for artwork dating.

```java
@Node("Year")
public class Year {
    @Id
    private String id;              // Unique identifier
    private Integer year;           // Year value
}
```

**Purpose**: Enables temporal organization and searching of artworks.

### Enumerations

#### ArtType
Defines the types of artwork supported.

```java
public enum ArtType {
    IMAGE,              // Static images
    VIDEO,              // Video content
    PODCAST_IMAGE,      // Podcast with image
    PODCAST_VIDEO       // Video podcast
}
```

#### Status
Defines the publication status of content.

```java
public enum Status {
    DRAFT,              // Not yet published
    PUBLISHED,          // Publicly available
    BLOCKED,            // Blocked by moderators
    APPROVED,           // Approved but not published
    DELETED             // Marked as deleted
}
```

#### StorageType
Defines where artwork media is stored.

```java
public enum StorageType {
    Firebase            // Stored in Firebase storage
}
```

## Relationship Models

### ArtistRelationship
Represents the relationship between an artist and their artwork.

### FollowRelationship
Represents a user following an artist.

### GalleryRelationship
Represents the relationship between an artist and a gallery they've created.

## Authentication Models

### JwtRequest
Used for login requests.

```java
public class JwtRequest {
    private String username;    // Username for login
    private String password;    // Password for login
}
```

### JwtResponse
Returned after successful authentication.

```java
public class JwtResponse {
    private String token;       // JWT token
    private String username;    // Authenticated username
    private List<String> roles; // User roles
}
```

### TokenRequest
Used for token validation.

```java
public class TokenRequest {
    private String token;       // JWT token to validate
}
```

### ForgetPasswordRequest
Used for password reset requests.

```java
public class ForgetPasswordRequest {
    private String phoneNumber; // User's phone number
    private String newPassword; // New password
}
```

## Model Relationships

The Artly system uses a rich graph database structure in Neo4j to model complex relationships:

1. **User-Artwork Interactions**:
    - Users can LIKE artworks
    - Users can DISLIKE artworks
    - Users can POST_COMMENT on artworks
    - Users can add artworks to FAVORITES collections

2. **Artist Relationships**:
    - Artists CREATED artworks
    - Users can FOLLOW artists
    - Artists BELONG_TO_GENRE genres

3. **Artwork Organization**:
    - Artworks BELONG to galleries
    - Artworks RELEASED_IN specific years
    - Artworks BELONG_TO_GENRE genres

4. **Social Features**:
    - Comments are linked to both users (POSTED_COMMENT) and artworks (HAS_COMMENT)
    - Users can create and manage multiple FAVORITES collections


## API Contract

### Authentication APIs

#### Login
- **Endpoint**: `/login`
- **Method**: POST
- **Request Body**:
  ```json
  {
    "username": "string",
    "password": "string"
  }
  ```**
- **Response**:
  ```json
  {
    "token": "string",
    "username": "string",
    "roles": ["string"]
  }
  ```

#### Signup
- **Endpoint**: `/signup`
- **Method**: POST
- **Request Body**:
  ```json
  {
    "username": "string",
    "password": "string",
    "phoneNumber": "string",
    "roles": ["string"]
  }
  ```
- **Response**: Same as login

### Artwork APIs

#### Get Recommended Artwork
- **Endpoint**: `/artwork/recommend`
- **Method**: GET
- **Parameters**:
  - `userId`: string
  - `skip`: integer
  - `limit`: integer
- **Response**: List of artwork objects

#### Get Popular Artwork
- **Endpoint**: `/artwork/popular`
- **Method**: GET
- **Parameters**:
  - `userId`: string
  - `skip`: integer
  - `limit`: integer
- **Response**: List of artwork objects

#### Get New Arrivals
- **Endpoint**: `/artwork/new-arrivals`
- **Method**: GET
- **Parameters**:
  - `userId`: string
  - `skip`: integer
  - `limit`: integer
- **Response**: List of artwork objects

#### Get Artwork by ID
- **Endpoint**: `/artwork/{userId}/{artworkId}`
- **Method**: GET
- **Response**: Artwork object

#### Like Artwork
- **Endpoint**: `/artwork/user/like/{artworkId}/{userId}`
- **Method**: PUT

#### Unlike Artwork
- **Endpoint**: `/artwork/user/unlike/{artworkId}/{userId}`
- **Method**: PUT

#### Dislike Artwork
- **Endpoint**: `/artwork/user/dislike/{artworkId}/{userId}`
- **Method**: PUT

### Artist APIs

#### Get Artist by ID
- **Endpoint**: `/artist/{artistId}`
- **Method**: GET
- **Response**: Artist object

#### Get All Artists
- **Endpoint**: `/artist/all`
- **Method**: GET
- **Response**: List of artist objects

### Comment APIs

#### Add Comment
- **Endpoint**: `/comment/add`
- **Method**: POST
- **Request Body**:
  ```json
  {
    "userId": "string",
    "artworkId": "string",
    "text": "string"
  }
  ```

#### Get Comments for Artwork
- **Endpoint**: `/comment/{artworkId}`
- **Method**: GET
- **Response**: List of comment objects

## Swagger Documentation

The API documentation is available through Swagger UI:

- **Local**: http://localhost:7040/swagger-ui/index.html
- **Production**: https://hammerhead-app-zgpcv.ondigitalocean.app/swagger-ui/index.html

The Swagger UI provides a comprehensive interface to explore and test all available APIs. It includes detailed information about request parameters, response formats, and authentication requirements.

## Security

The application uses JWT (JSON Web Token) for authentication. All API endpoints (except login, signup, and public endpoints) require a valid JWT token in the Authorization header:

```
Authorization: Bearer <your-jwt-token>
```

## Deployment

The application is deployed on DigitalOcean and can be accessed at:
https://hammerhead-app-zgpcv.ondigitalocean.app/

## License

This project is licensed under the MIT License - see the LICENSE file for details.
