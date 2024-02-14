package bguspl.set.ex;

import bguspl.set.Env;
import java.util.Queue;
import java.util.ArrayDeque;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Iterator;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;
    private ArrayDeque<Integer> checkIfSet; // player that want the dealer to check its set will push its id to here.

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.checkIfSet = new ArrayDeque<Integer>();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * When the user clicks the close window button, the class WindowManager that we provided you
         * with, automatically calls Dealer::terminate method of the dealer thread, and Player::terminate
         * method for each opened player thread. 
         */
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // assuming queuePlayerTokens has slots nums
        synchronized (table) {
            Iterator<Integer> iterator = checkIfSet.iterator();
            while (iterator.hasNext()) {
                Integer playerId = iterator.next();
                for (int i = 0 ; i < players.length ; i++){
                    if (players[i].id == playerId){
                        Player player = players[i];
                        // check if player.queuePlayerTokens is a set using env.util.testSet
                        int [] cards = new int[player.queuePlayerTokens.size()]; // size() = 3
                        int j = 0;
                        for (Integer slot : player.queuePlayerTokens){ // create array of the cards
                            cards[j] = table.slotToCard[slot];
                            j++;
                        }
                        boolean giveScore = env.util.testSet(cards); // check if its a set
                        if (giveScore == true){ // if yes, add point and remove cards
                            player.point();
                            for (int card : cards){
                                table.removeCard(table.cardToSlot[card]);
                            }
                        }
                        if (giveScore == false){ // if no, give panelty
                            player.penalty();
                        }
                    }
                }
            }
        }

    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        synchronized (table) {
            int size = Math.min(deck.size(), env.config.tableSize);
            for (int i = 0 ; i < size && table.countCards() < env.config.tableSize ; i++){
                int avaliableSlot = table.avaliableSlot(); // because of the condition in the loop - it will never be -1.
                table.placeCard(deck.get(i), avaliableSlot);
                deck.remove(i);
            }
        }
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // once every minute the dealer collects all the cards from the table, reshuffles the deck and draws them anew.
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // Collecting the cards back from the table when needed
        synchronized (table) {
            for ( int slot : table.cardToSlot){
                deck.add(table.slotToCard[slot]);
                table.removeCard(slot);
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        int maxScore = Integer.MIN_VALUE;
        for (Player player : players){
            if (player.score() > maxScore){
                maxScore = player.score();
            }
        }
        int winnersCount = 0;
        for (Player player : players){
            if (player.score() == maxScore){
                winnersCount++;
            }
        }

        int [] winners = new int [winnersCount];
        int i = 0;
        for (Player player : players){
            if (player.score() == maxScore){
                winners[i] = player.id;
                i++;
            }
        }
        env.ui.announceWinner(winners);

        // does this terminate the game without closing the window??????
    }

}
