# Chess Game

A real-time multiplayer chess application built with Spring Boot and WebSocket, enabling two players to play chess together with instant move synchronization.

## Features

- **Real-time Multiplayer**: Play chess with another player using WebSocket communication
- **Complete Chess Rules**: Full implementation of chess piece movement and validation
  - All standard piece types (Pawn, Rook, Knight, Bishop, Queen, King)
  - Path validation to prevent illegal jumps
  - Turn-based gameplay with move history
- **Multiple Games**: Support for multiple concurrent games with unique game IDs
- **Spectator Mode**: Watch ongoing games without participating
- **Responsive UI**: Mobile-friendly chess board interface, toned color, great design
- **Game States**: Track game progression (waiting, in progress, completed, abandoned)
- **Auto-reconnection**: WebSocket client automatically reconnects on connection loss

## Technology Stack

### Backend
- Java 17
- Spring Boot 3.5.5
- Spring WebFlux (reactive web framework)
- Spring WebSocket (real-time communication)
- Spring Data MongoDB Reactive
- Lombok

### Frontend
- HTML5 with Thymeleaf templates
- Vanilla JavaScript
- WebSocket API
- Responsive CSS

### Database
- MongoDB (reactive driver)

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- MongoDB 4.4+ (or MongoDB Atlas for cloud deployment)

## Environment Variables

The application requires the following environment variable:

- `MONGODB_URI`: MongoDB connection string (defaults to `mongodb://localhost:27017/chess`)

Example for MongoDB Atlas:
```bash
export MONGODB_URI="mongodb+srv://username:password@cluster.mongodb.net/chess?retryWrites=true&w=majority"
```

## Deployment

### Render.com

1. Create a new Web Service on Render.com
2. Connect your GitHub repository
3. Set the following:
   - **Build Command**: `mvn clean package -DskipTests`
   - **Start Command**: `java -jar target/chess-0.0.1-SNAPSHOT.jar`
4. Add environment variable:
   - Key: `MONGODB_URI`
   - Value: Your MongoDB Atlas connection string
5. Deploy!

### Local Development

1. Clone the repository
2. Set the MongoDB URI environment variable (or use default localhost)
3. Run the application:
   ```bash
   mvn spring-boot:run
   ```
4. Open http://localhost:8080