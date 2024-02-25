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
        System.out.println("Tamar: ________ "+"Player : "+id+" run()");
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            while(playerActions.size() == 0){
                try { wait(1000);
                } catch (Exception e) {}
            }
            if(queuePlayerTokens.size() < env.config.featureSize & !terminate){
                System.out.println("tamar: __________________ player " + id + "queuePlayerTokens size is " + queuePlayerTokens.size());
                int slot = playerActions.remove();
                boolean toRemove;
                synchronized(queuePlayerTokens){
                    toRemove = queuePlayerTokens.remove(slot);
                }

                if (!toRemove) {
                    synchronized(queuePlayerTokens){
                        queuePlayerTokens.add(slot);
                    }
                    synchronized(table){
                        table.placeToken(id, slot);
                    }
                }
                else {
                    System.out.println("tamar: __________________ player " + id + "queuePlayerTokens contains the slot to remove " + queuePlayerTokens.contains(slot));
                    synchronized(table){
                        table.removeToken(id, slot);
                    }
                }
                System.out.println("Tamar: ----- player "+ id +":  queueplayertoken size is:" + queuePlayerTokens.size());
            }
            if ((queuePlayerTokens.size() == env.config.featureSize)){
                System.out.println("Tamar:_______________player" + id + "ask for checkset");
                dealer.checkIfSet.add(id);
                //dealer.notify();
                while(!waitForDealreAnswer){}                       //1 player choosed an incorrect set ->> found set  = false, waitForDealreAnswer = true;
                if (dealerAnswer){
                    if(foundSet){
                        point();
                    }                  //2 player chooses another incorrect set -->
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
                while ((queuePlayerTokens.size() < env.config.featureSize) & !terminate) {
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
        System.out.println("Tamar: ________ "+"Player : "+id+" terminate()");
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
        System.out.println("Tamar: ________ "+"Player : "+id+" keyPressed()");
        synchronized(playerActions){
        this.playerActions.add(slot);
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        System.out.println("Tamar: ________ "+"Player : "+id+" point()");
        removeTokens();
        // int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        try {
            env.ui.setFreeze(id, env.config.pointFreezeMillis);
            Thread.sleep(env.config.pointFreezeMillis); // sleeps for 1 sec
        } catch (InterruptedException e) {}
        env.ui.setFreeze(id, 0);
        foundSet = false;
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        System.out.println("Tamar: ________ "+"Player : "+id+" panelty()");
        removeTokens();
        try {
            for(long i = env.config.penaltyFreezeMillis/1000; 0< i; i--){ // sleep for 3 seconds
                env.ui.setFreeze(id, i * 1000);
                Thread.sleep(1000); // sleep for 3 seconds
            }
        } catch (InterruptedException e) {}
        env.ui.setFreeze(id, 0);
    }

    public int score() {
        System.out.println("Tamar: ________ "+"Player : "+id+" score()");
        return score;
    }

    /**
     * removes the player tokens and empties the queue.
     * 
     * @post - the tables doesn't display the player tokens
     * @post - queuePlayerTokens is empty
     */
    public void removeTokens(){
        System.out.println("Tamar: ________ "+"Player : "+id+" removeTokens()");
        synchronized(table){
            while(!queuePlayerTokens.isEmpty()){
                table.removeToken(id, queuePlayerTokens.remove());
            }
            System.out.println("remove tokens after removing from player tokents " + queuePlayerTokens.size());
            synchronized(playerActions){
                this.playerActions.clear();
            }
        }
        
    }

    public Thread getThread(){
        System.out.println("Tamar: ________ "+"Player : "+id+" getThread()");
        return playerThread;
    }
}