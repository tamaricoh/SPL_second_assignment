package bguspl.set.ex;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Iterator;
import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The queue keeping  the key presses that a player did.
     */

     Queue<Integer> queuePlayerTokens;
    
     /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        queuePlayerTokens = new ArrayDeque<>(3);
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop
            // TODO implement~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            // tries to find a set
            // send to the dealer right after the thired card got picked
            // if the set is legel - update the score.
            // check for panelty???
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                // TODO implement~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                // simulate the keyboard clicking
                // chose 3 cards from the table, randomly (its actually chose 3 slots from the table)

                // The player thread consumes the actions from the queue, placing or removing a token in the corresponding slot in the grid on the table.
                // Once the player places his third token on the table, he must notify the dealer and wait until the dealer checks if it is a legal set or not. The dealer then gives him either a point or a penalty accordingly.

                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        // TODO implement~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * When the user clicks the close window button, the class WindowManager that we provided you
         * with, automatically calls Dealer::terminate method of the dealer thread, and Player::terminate
         * method for each opened player thread. 
         */
        /**
         * +2 points will be awarded for terminating all threads (that you created) gracefully and
         * in reverse order to the order they were created in.
         */
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        Iterator<Integer> iter = queuePlayerTokens.iterator();
        Boolean toRemove = false;
        while(iter.hasNext()){                  // check if the slot was chosen already and remove it if so
            if(iter.next() == slot) {
                table.removeToken(id, slot);
                toRemove = true;
            }
        }
        if(!toRemove & queuePlayerTokens.size() < 3){   //if it's a new slot then add it to the table
            queuePlayerTokens.add(slot);
            table.placeToken(id, slot);
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        removeTokens(); 
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        try {
            env.ui.setFreeze(id, env.config.pointFreezeMillis);
            Thread.sleep(env.config.pointFreezeMillis); // sleeps for 1 sec
        } catch (InterruptedException e) {}
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        removeTokens();
        try {
            env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
            Thread.sleep(env.config.penaltyFreezeMillis); // sleep for 3 seconds
        } catch (InterruptedException e) {}
    }

    public int score() {
        return score;
    }

    /**
     * removes the player tokens and empties the queue.
     * 
     * @post - the tables doesn't display the player tokens
     * @post - queuePlayerTokens is empty
     */
    private void removeTokens(){
        while(!queuePlayerTokens.isEmpty()){
            int slot = (int) queuePlayerTokens.remove();
            table.removeToken(id, slot);
        }
    }
}