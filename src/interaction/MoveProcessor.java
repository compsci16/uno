package interaction;

import exceptions.IllegalCardStringException;
import exceptions.IllegalMoveException;
import model.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static model.CardUtility.*;
import static model.GameStatus.*;
import static model.Speciality.*;

/**
 * This class handles every player move made by any player of the game at any time.
 */
public final class MoveProcessor {

    private MoveProcessor() {
        throw new IllegalStateException("Utility class");
    }

    /**
     *
     * @param game The game to process
     * @param player The player who made the move
     * @param move The move made by the player
     * @throws IllegalMoveException When the move is disallowed.
     * @throws IllegalCardStringException When the card is disallowed.
     * @throws IOException When an IO processing error occurs.
     */
    public static void processMove(Game game, Player player, String move) throws IllegalMoveException, IllegalCardStringException, IOException {

        if (move.equals("VIEW CARDS")) {
            informPlayerOfCardSet(game, player);
            return;
        }

        if (move.equals("VIEW CARDS -O")) {
            informPlayerOfOthersNumberOfCards(game, player);
            return;
        }

        checkTurn(game, player);

        if (move.equals("UNO")) {
            processSaidUNO(game, player);
            return;
        }

        if (move.equals("DRAW")) {
            processDrawACard(game, player);
            return;
        }

        Card discardPileTop = game.getDiscardPile().peekTopCard();
        Card playedCard = CardUtility.stringRepToCard(move);
        CardSet playerCardSet = player.getCardSet();

        if (!playerCardSet.contains(playedCard))
            throw new IllegalMoveException("You do not have this card!");

        if (isDrawnCardNotPlayableOnTopCard(playedCard, discardPileTop))
            throw new IllegalMoveException("Your card color/number/speciality does not match top card on pile!");

        if (playedCard instanceof NumberedCard playedNumberedCard)
            processNumberedCard(game, player, playedNumberedCard);
        else if (playedCard instanceof SpecialColoredCard playedSpecialColoredCard)
            processSpecialColoredCard(game, player, playedSpecialColoredCard);

        if (player.getCardSet().size() == 1)
            game.setStatus(UNO_PENDING_MODE);

        if (player.getCardSet().isEmpty()) {
            game.setStatus(FINISHED);
            broadCastMoveInfo(game, player, player.getName() + " won the game", "YOU WIN!");
        }


    }

    private static void checkTurn(Game game, Player player) throws IllegalMoveException {
        if (game.getStatus().equals(WAITING_FOR_PLAYERS))
            throw new IllegalMoveException("Insufficient players!");

        if (!game.getStatus().equals(UNO_PENDING_MODE) && game.getCurrentPlayer() != player)
            throw new IllegalMoveException("Not your turn!");

        if (game.getStatus().equals(FINISHED))
            throw new IllegalMoveException("model.Game is over!");
    }

    private static void processSaidUNO(Game game, Player player) {
        if (player.getCardSet().size() == 1) {
            broadCastMoveInfo(game, player,
                    player.getName() + " said UNO!",
                    "Successful UNO!");
            game.setStatus(IN_PROGRESS);
        } else {
            for (Player p : game.getPlayers()) {
                if (p.getCardSet().size() == 1) {
                    Player playerWithOneCard = p;
                    playerWithOneCard.getCardSet().add(game.getDrawPile().popTopCard());
                    playerWithOneCard.getCardSet().add(game.getDrawPile().popTopCard());

                    String messageToUNOd = "You were UNO'd by " + player.getName();
                    String messageToUNOer = "You successfully UNO'd " + playerWithOneCard.getName() + " who had to draw 2 cards";
                    String messageToOthers = player.getName() + " successfully UNO'd" + playerWithOneCard.getName()
                            + " who had to draw 2 cards";

                    informPlayer(game, playerWithOneCard, messageToUNOd);
                    informPlayer(game, player, messageToUNOer);
                    informPlayerOfCardSet(game, playerWithOneCard);
                    broadcastToAllExcept(game, player, messageToOthers);
                    game.setStatus(IN_PROGRESS);
                    return;
                }
            }
        }
    }

    private static void processDrawACard(Game game, Player player) throws IOException, IllegalMoveException, IllegalCardStringException {
        Card drawnCard = game.getDrawPile().popTopCard();
        player.getCardSet().add(drawnCard);
        String messageToPlayer = "You drew the card " + drawnCard;
        String messageToOthers = player.getName() + " drew a card.";
        informPlayer(game, player, messageToPlayer);

        Card topCard = game.getDiscardPile().peekTopCard();
        if (isDrawnCardNotPlayableOnTopCard(drawnCard, topCard)) {
            broadcastToAllExcept(game, player, messageToOthers);
            informPlayerOfCardSet(game, player);
            game.incrementCurrentPlayerIndex();
        } else {
            String decision;
            do {
                decision = game.getGameServer().askPlayer("play or skip?", player).toLowerCase();
            } while (!(decision.equals("play") || decision.equals("skip")));
            broadcastToAllExcept(game, player, messageToOthers);

            if (decision.equals("play")) {
                broadcastToAllExcept(game, player, player.getName() + " is playing the drawn card!");
                processMove(game, player, drawnCard.toString().replace(":", ""));
            } else {
                informPlayerOfCardSet(game, player);
                game.incrementCurrentPlayerIndex();
            }
        }

    }

    private static boolean isDrawnCardNotPlayableOnTopCard(Card drawnCard, Card topCard) {
        return !areBothColoredAndHaveSameColor(drawnCard, topCard) &&
                !areBothNumberedAndHaveSameNumber(drawnCard, topCard) &&
                !areBothSpecialAndHaveSameSpeciality(drawnCard, topCard);
    }

    private static void processSpecialColoredCard(Game game, Player player, SpecialColoredCard playedCard) {
        if (playedCard.getSpeciality().equals(SKIP))
            processSkipCard(game, player, playedCard);
        if (playedCard.getSpeciality().equals(DRAW2))
            processDraw2Card(game, player, playedCard);
        if (playedCard.getSpeciality().equals(REVERSE))
            processReverseCard(game, player, playedCard);
    }

    private static void processNumberedCard(Game game, Player player, NumberedCard card) {
        player.getCardSet().remove(card);
        game.getDiscardPile().addCard(card);

        String messageToPlayer = "You played " + card;
        String messageToOthers = player.getName() + " played " + card;
        broadCastMoveInfo(game, player, messageToOthers, messageToPlayer);
        informPlayerOfCardSet(game, player);
        game.incrementCurrentPlayerIndex();
    }

    private static void processDraw2Card(Game game, Player player, SpecialColoredCard playedCard) {
        player.getCardSet().remove(playedCard);
        game.getDiscardPile().addCard(playedCard);

        Pile drawPile = game.getDrawPile();
        Player toBeSkipped = game.getNextPlayer();
        Card[] draw2Card = new Card[2];
        for (int i = 0; i < 2; i++) {
            Card popped = drawPile.popTopCard();
            draw2Card[i] = popped;
            toBeSkipped.getCardSet().add(popped);
        }


        String messageToOthers = player.getName() + " played " + playedCard;
        String messageToPlayer = "You made " + toBeSkipped.getName() + " draw 2 cards";
        String messageToSkipped = "You have to draw the following 2 cards: " + Arrays.toString(draw2Card);

        broadCastMoveInfo(game, player, messageToOthers, messageToPlayer);
        informPlayerOfCardSet(game, player);
        informPlayerOfCardSet(game, toBeSkipped);
        informPlayer(game, toBeSkipped, messageToSkipped);

        game.incrementCurrentPlayerIndex(); // to skip next player
        game.incrementCurrentPlayerIndex();

    }

    private static void processSkipCard(Game game, Player player, SpecialColoredCard playedCard) {
        player.getCardSet().remove(playedCard);
        game.getDiscardPile().addCard(playedCard);

        Player skippedPlayer = game.getNextPlayer();
        String skippedUserName = skippedPlayer.getName();
        String info = " played a skip of " +
                playedCard.getColor().toString().toLowerCase() + " color.";

        String messageToOthers = player.getName() + info;
        String messageToPlayer = "You " + info;

        game.incrementCurrentPlayerIndex(); // to skip next player
        game.incrementCurrentPlayerIndex();

        broadCastMoveInfo(game, player, messageToOthers, messageToPlayer);

        game.getGameServer().messagePlayer("Your turn skipped ", skippedPlayer);
        game.getGameServer().broadcastToAllExcept(skippedUserName + "'s turn skipped", skippedPlayer);

    }

    private static void processReverseCard(Game game, Player player, SpecialColoredCard playedCard) {
        player.getCardSet().remove(playedCard);
        game.getDiscardPile().addCard(playedCard);

        Collections.reverse(game.getPlayers());
        game.setCurrentPlayerIndex(game.getMaxPlayers() - game.getCurrentPlayerIndex() - 1);

        String info = " reversed the order ";

        String messageToPlayer = "You " + info;
        String messageToOthers = player.getName() + info;

        broadCastMoveInfo(game, player, messageToOthers, messageToPlayer);

        game.getGameServer().broadcast("Now the order of moves is: " + game.getPlayers());

        informPlayerOfCardSet(game, player);

        game.incrementCurrentPlayerIndex();

    }

    private static void broadcastToAllExcept(Game game, Player excluded, String message) {
        game.getGameServer().broadcastToAllExcept(message, excluded);
    }

    private static void broadCastMoveInfo(Game game, Player player, String messageToOthers, String messageToPlayer) {
        game.getGameServer().broadcastMessage1ToOthersMessage2ToPlayer(messageToOthers,
                messageToPlayer, player);
    }

    private static void informPlayerOfCardSet(Game game, Player player) {
        game.getGameServer().messagePlayer("Your cards now:\n" + player.getCardSet(), player);
    }

    private static void informPlayerOfOthersNumberOfCards(Game game, Player player) {
        var info = new StringBuilder();
        for (var p : game.getPlayers())
            info.append(p.getName()).append(": ").append(p.getCardSet().size()).append("\n");
        informPlayer(game, player, info.toString());
    }

    private static void informPlayer(Game game, Player player, String message) {
        game.getGameServer().messagePlayer(message, player);
    }


}
