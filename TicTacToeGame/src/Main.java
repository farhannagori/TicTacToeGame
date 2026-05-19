import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    public static void main(String[] args) {
        System.out.println("Game stated!");
        TicTacToe game = new TicTacToe(3);
        //game.printGameBoard();
        playGame(1, 1, Piece.X, game);
        playGame(1, 0, Piece.O, game);
        playGame(2, 2, Piece.X, game);
        playGame(2, 0, Piece.O, game);
        playGame(0, 0, Piece.X, game);
        playGame(0, 1, Piece.O, game);

        game.printGameBoard();
    }

    public static void playGame(int row, int column, Piece piece, TicTacToe game) {
        System.out.println(game.movePlayer(row, column, piece));
    }
}

enum Piece {X, O, E}
enum GameStatus {IN_PROGRESS, WON, DRAW};

class Game {
    ConcurrentHashMap<String, TicTacToe> games = new ConcurrentHashMap<>();

    Game() {

    }
}
class TicTacToe {

    int n = 3;
    Piece currentPlayer = Piece.X;
    GameStatus gameStatus = GameStatus.IN_PROGRESS;
    Piece board[][];
    int rowCount[];
    int columnCount[];
    int diagonalCount = 0;
    int antiDiagonalCount = 0;
    int totalMoveLogged = 0;

    TicTacToe(int n) {
        if(n < 3)
            throw new RuntimeException("board can not be less then 3*3");

        board = new Piece[n][n];
        rowCount = new int[n];
        columnCount = new int[n];


        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                board[i][j] = Piece.E;
            }
        }
    }

    public void printGameBoard() {
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                System.out.print(" [" + board[i][j] + "] ");
            }
            System.out.println("\n");
        }
    }

    public GameStatus movePlayer(int row, int column, Piece piece) {
        validateInput(row, column, piece);
        recordGameState(row, column, piece);
        toggleGamePlayer();
        return gameStatus;


    }

    public void validateInput(int row, int column, Piece piece) {
        if(row < 0 || row >= n || column < 0 || column >= n) throw new RuntimeException("Invalid move");
        if(board[row][column] != Piece.E) throw new RuntimeException("This move is already taken");
        if(piece != currentPlayer) throw new RuntimeException("This is Player" + currentPlayer + "turn");
        if(gameStatus != GameStatus.IN_PROGRESS) throw new RuntimeException("This game is already finished, Play a new Game");
    }

    public void toggleGamePlayer() {
        currentPlayer = (currentPlayer == Piece.X) ? Piece.O : Piece.X;
    }
    public void recordGameState(int row, int column, Piece piece) {
        board[row][column] = piece;
        totalMoveLogged +=1;
        int weigh = piece == Piece.X ? 1 : -1;
        rowCount[row]+=weigh;
        columnCount[column]+=weigh;

        if(row == column) {
            diagonalCount +=weigh;
        }

        if(row + column == n - 1) {
            antiDiagonalCount +=weigh;
        }
        gameStatus = checkGameStatus(row, column);

    }
    public GameStatus checkGameStatus(int row, int column) {

        if(Math.abs(rowCount[row]) == n || Math.abs(columnCount[column]) == n || Math.abs(diagonalCount) == n || Math.abs(antiDiagonalCount) == n)
            return GameStatus.WON;
        if(totalMoveLogged == n*n) return GameStatus.DRAW;
        return GameStatus.IN_PROGRESS;
    }
}