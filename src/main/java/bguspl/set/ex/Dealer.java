package bguspl.set.ex;

import bguspl.set.Env;
import java.util.Queue;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    volatile public ArrayDeque<Integer> checkIfSet; // player that want the dealer to check its set will push its id to here.
    private long timeLoopStarted;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;
    private Queue<Integer> setAttempt;
    private boolean correctSet;
    // private ArrayDeque<Thread> playersThreads;


    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;
    

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private int playerToCheckID;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.checkIfSet = new ArrayDeque<Integer>();
        this.setAttempt = new ArrayDeque<Integer>();
        this.correctSet = false;
        this.terminate = false;
        this.reshuffleTime = env.config.turnTimeoutMillis;
        this.playerToCheckID = -1;
        Collections.shuffle(deck);
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        
        for (Player player : players) {
            Thread playersThread = new Thread(()-> player.run()); 
            playersThread.start();
        }

        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        announceWinners();
        terminate();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        this.timeLoopStarted = System.currentTimeMillis();
        while (!terminate && System.currentTimeMillis() < timeLoopStarted + reshuffleTime) { 
            sleepUntilWokenOrTimeout(); // rest or check set
            checkForSet();
            if (correctSet) timeLoopStarted = System.currentTimeMillis();
            updateTimerDisplay(correctSet); 
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        for (int i = players.length - 1; i >= 0; i--) 
            players[i].terminate();
        terminate = true; 
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
        synchronized(table){
        if (correctSet){
                while (!setAttempt.isEmpty()) {
                    int slot = setAttempt.poll();
                    table.removeCard(slot);
                    for (Player player : players){
                        synchronized (player.queuePlayerTokens){
                            if(player.id != playerToCheckID && player.queuePlayerTokens.remove(slot)){
                                checkIfSet.remove(player.id);
                                player.waitForDealreAnswer = true;
                            }
                        }
                    }
                }
            }
            this.correctSet = false;
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        synchronized(table){
        int tableSize = env.config.tableSize;
        int size = Math.min(deck.size(), tableSize);
        int numOfCardsOnTable = table.countCards();
            for (int i = 0 ; i < size &&  numOfCardsOnTable < tableSize ; i++){
                int avaliableSlot = table.avaliableSlot(); // because of the condition in the loop - it will never be -1.
                table.placeCard(deck.remove(0), avaliableSlot);
                numOfCardsOnTable++;
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private synchronized void sleepUntilWokenOrTimeout() {
        try {
            // Wait for either a notification or for one second
            wait(1000);
        } catch (InterruptedException e) {}


    }

    private void checkForSet(){
        if (!checkIfSet.isEmpty()) {
            playerToCheckID = checkIfSet.poll();
            Player player = players[playerToCheckID];
            int [] cards = new int [env.config.featureSize];
            int i = 0;
            synchronized (player.queuePlayerTokens){
                this.setAttempt =  player.queuePlayerTokens.clone();
                for (Integer token : player.queuePlayerTokens){
                    synchronized(table){
                        cards[i] = table.slotToCard[token];
                    }
                    i++;
                }
            }
            this.correctSet = env.util.testSet(cards);
            player.foundSet = correctSet;
            player.waitForDealreAnswer = true;
            player.dealerAnswer = true;
            return;
            
        } 
    }


    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        long currentTime = System.currentTimeMillis();
        boolean needWarning = (env.config.turnTimeoutWarningMillis >= env.config.turnTimeoutMillis-currentTime+timeLoopStarted) & !reset;
        if (reset){
            env.ui.setCountdown(env.config.turnTimeoutMillis, needWarning);
            return;
        }
        env.ui.setCountdown(Math.max(reshuffleTime-currentTime+timeLoopStarted,0), needWarning);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // Collecting the cards back from the table when needed (after a minute or when there are no sets on the table)
        synchronized (table) {
            for ( Integer slot : table.cardToSlot){
                if (slot != null){
                    deck.add(table.slotToCard[slot]);
                    table.removeCard(slot);
                }
            }
            for (Player player : players){
                player.removeTokens();
            }
            while (!checkIfSet.isEmpty()) {
                int id = checkIfSet.remove();
                players[id].waitForDealreAnswer = true;
            }
            checkIfSet.clear();
        }
        Collections.shuffle(deck);
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int maxScore = Integer.MIN_VALUE;
        for (Player player : players){
            if (player.score() > maxScore){
                maxScore = player.score();
            }
        }
        ArrayDeque<Player> winnersPlayers = new ArrayDeque<>();
        for (Player player : players){
            if (player.score() == maxScore){
                winnersPlayers.add(player);
            }
        }
        int[] winnersID = new int[winnersPlayers.size()];
        int i = 0;
        for (Player player : winnersPlayers){
            winnersID[i] = player.id;
            i++;
        }
        env.ui.announceWinner(winnersID);
    }

}
