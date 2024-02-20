package bguspl.set.ex;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Iterator;
import bguspl.set.Env;
import java.util.Random;

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

     volatile Queue<Integer> queuePlayerTokens;

     public boolean checked;
     public boolean foundSet;
     Dealer dealer;
    
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
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        terminate = false;
        queuePlayerTokens = new ArrayDeque<>(env.config.featureSize);
        checked = false;
        foundSet = false;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void  run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            synchronized(this){
                while(queuePlayerTokens.size() < env.config.featureSize & !terminate){      //wait for player to select 3 cards
                    try {
                        playerThread.wait();
                    } catch (Exception e){}
                }
                if(!checked & !terminate) notifyDealer();
            }
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
                while ((queuePlayerTokens.size() < env.config.featureSize | checked) & !terminate) {
                    Random rand = new Random();
                    int randomSlot = rand.nextInt(env.config.tableSize + 1);
                    keyPressed(randomSlot);
                }
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        synchronized (this){
            this.notifyAll();
        }
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
        Boolean toRemove = false;
        for(int token : queuePlayerTokens){        // check if the slot was chosen already and remove it if so
            if(token == slot) {
                table.removeToken(id, slot);
                toRemove = true;
                queuePlayerTokens.remove(token);
                checked = false;
            }
        }
        if(!toRemove && queuePlayerTokens.size() < env.config.featureSize){   //if it's a new slot then add it to the table
            queuePlayerTokens.add(slot);
            table.placeToken(id, slot);
            checked = false;
            if(queuePlayerTokens.size() == env.config.featureSize){
                this.notifyAll();
            }
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
        checked = false;
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
            Integer slot = queuePlayerTokens.remove();
            table.removeToken(id, slot);
        }
    }

    private void notifyDealer(){
        Thread thread = Thread.currentThread(); 
        dealer.checkIfSet.add(id);
        dealer.notify(); 
        try {
            thread.wait();
        } catch (Exception e){}
        if (foundSet){                         // reward or punish accordingly
            point();
            checked = false;
        }
        else{
            penalty();
            checked = true;
        }
    }
}