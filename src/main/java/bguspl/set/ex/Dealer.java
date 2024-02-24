package bguspl.set.ex;

import bguspl.set.Env;
import java.util.Queue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        System.out.println("Tamar: ________ "+"Dealer : "+" run()");
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
        System.out.println("Tamar: ________ "+"Dealer : "+" timerLoop()");
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
        System.out.println("Tamar: ________ "+"Dealer : "+" terminate()");
        // for (Player player : players){
        //     player.terminate();
        // }
        // for (Player player : players){
        //     try{
        //         player.getThread().join(); 
        //     } catch (InterruptedException e){}
        // }
        // terminate = true;
        // this.notifyAll();
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
        System.out.println("Tamar: ________ "+"Dealer : "+" shoulFinish()");
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        System.out.println("Tamar: ________ "+"Dealer : "+" removeCardsFromTable()");
        if (correctSet){
            System.out.println("Tamar: ----- "+"Dealer : "+" removeCardsFromTable() : "+ "inside if");
            System.out.println("Tamar: ----- "+"Dealer : "+" removeCardsFromTable() : "+ setAttempt.size());
            synchronized(table){
                while (!setAttempt.isEmpty()) {
                    System.out.println("Tamar: ----- " + "Dealer : " + " removeCardsFromTable() : " + "inside while loop");
                    int slot = setAttempt.poll();
                    table.removeCard(slot);
                    for (Player player : players){
                        synchronized (player.queuePlayerTokens){
                            if(player.queuePlayerTokens.remove(slot)){
                                checkIfSet.remove(player.id);
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
        System.out.println("Tamar: ________ "+"Dealer : "+" placeCardsOnTable()");
        int tableSize = env.config.tableSize;
        int size = Math.min(deck.size(), tableSize);
        int numOfCardsOnTable = table.countCards();
        synchronized(table){
            for (int i = 0 ; i < size &&  numOfCardsOnTable < tableSize ; i++){
                int avaliableSlot = table.avaliableSlot(); // because of the condition in the loop - it will never be -1.
                table.placeCard(deck.get(i), avaliableSlot);
                numOfCardsOnTable++;
                deck.remove(i);
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private synchronized void sleepUntilWokenOrTimeout() {
        System.out.println("Tamar: ________ "+"Dealer : "+" sleepUntilWokenOrTimeout()");
        try {
            // Wait for either a notification or for one second
            wait(1000);
        } catch (InterruptedException e) {}


    }

    private void checkForSet(){
        System.out.println("Tamar: ________ "+"Dealer : "+" checkForSet()");
        if (!checkIfSet.isEmpty()) {
            System.out.println("Tamar: -------- "+"checkForSet() : "+" enter first if");
            Integer playrerToCheckId = checkIfSet.poll();
            Player player = players[playrerToCheckId];
            int [] cards = new int [env.config.featureSize];
            int i = 0;
            synchronized (player.queuePlayerTokens){
                this.setAttempt =  player.queuePlayerTokens;
                for (Integer token : player.queuePlayerTokens){
                    synchronized(table){
                        cards[i] = table.slotToCard[token];
                    }
                    i++;
                }
            }
            this.correctSet = env.util.testSet(cards);
            String Tamar = correctSet? "real" : "not real";
            System.out.println("Tamar: -------- "+"checkForSet() : "+" the set is "+Tamar);
            player.foundSet = correctSet;
            player.checked = true;
            return;
            
        } 
    }


    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        System.out.println("Tamar: ________ "+"Dealer : "+" updateTimerDisplay()");
        boolean needWarning = (env.config.turnTimeoutWarningMillis >= System.currentTimeMillis());
        if (reset){
            env.ui.setCountdown(env.config.turnTimeoutMillis, needWarning);
            return;
        }
        env.ui.setCountdown(reshuffleTime-System.currentTimeMillis()+timeLoopStarted, needWarning);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        System.out.println("Tamar: ________ "+"Dealer : "+" removeAllCardsFromTable()");
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
            checkIfSet.clear();
        }
        Collections.shuffle(deck);
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        System.out.println("Tamar: ________ "+"Dealer : "+" announceWinners()");
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
