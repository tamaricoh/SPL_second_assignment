package bguspl.set.ex;
import java.util.ArrayDeque;
import java.util.Queue;
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

     volatile ArrayDeque<Integer> queuePlayerTokens;
     private volatile Queue<Integer> playerActions;
     volatile public boolean foundSet;
     volatile boolean waitForDealreAnswer; // the player sent a set to the dealer
     volatile boolean dealerAnswer; // true if the dealer checked the set, false if the dealer could nou check the set because another player took it
    //  volatile boolean waitForDealreAnswer; // this player asked the dealer to check a set and the dealer handled it.
    //  volatile boolean setNoLongerAvaliable; // 
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
        playerActions = new ArrayDeque<>();
        foundSet = false;
        waitForDealreAnswer = false;
        dealerAnswer = false;
        
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
            while(playerActions.size() == 0){
                try { wait(1000);
                } catch (Exception e) {}
            }
            if(queuePlayerTokens.size() < env.config.featureSize & !terminate){
                int slot = playerActions.remove();
                boolean toRemove;
                synchronized(queuePlayerTokens){
                    toRemove = queuePlayerTokens.remove(slot);
                }

                if (!toRemove) {
                    synchronized(queuePlayerTokens){
                        synchronized(table){
                            if(table.slotToCard[slot] != null){
                            queuePlayerTokens.add(slot);
                            table.placeToken(id, slot);
                            }
                        }
                    }
                }
                else {
                    synchronized(table){
                        table.removeToken(id, slot);
                    }
                }
            }
            if ((queuePlayerTokens.size() == env.config.featureSize)){
                dealer.checkIfSet.add(id);
                while(!waitForDealreAnswer & !terminate){}
                if (dealerAnswer){
                    if(foundSet){
                        point();
                    }           
                    else{
                        penalty();
                    }
                    dealerAnswer = false;
                }
                synchronized(playerActions){
                    this.playerActions.clear();
                }
                waitForDealreAnswer = false;
            }
           
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() { //to update
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                if ((playerActions.size() < env.config.featureSize) & !waitForDealreAnswer & !terminate) {
                    Random rand = new Random();
                    int randomSlot = rand.nextInt(env.config.tableSize);
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
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        synchronized(table){
            synchronized(playerActions){
                if(table.slotToCard[slot] != null){
                    this.playerActions.add(slot);
                }  
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
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        try {
            for(long i = env.config.pointFreezeMillis/1000; 0< i; i--){
                env.ui.setFreeze(id, i * 1000);
                Thread.sleep(1000); // sleep for 3 seconds
            }
        } catch (InterruptedException e) {}
        env.ui.setFreeze(id, 0);
        foundSet = false;
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        removeTokens();
        try {
            for(long i = env.config.penaltyFreezeMillis/1000; 0< i; i--){ // sleep for a few seconds
                env.ui.setFreeze(id, i * 1000);
                Thread.sleep(1000); // sleep for 3 seconds
            }
        } catch (InterruptedException e) {}
        env.ui.setFreeze(id, 0);
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
    public void removeTokens(){
        synchronized(table){
            synchronized(queuePlayerTokens){
                while(!queuePlayerTokens.isEmpty()){
                    table.removeToken(id, queuePlayerTokens.remove());
                }
            }
            synchronized(playerActions){
                this.playerActions.clear();
            }
        }
        
    }
}