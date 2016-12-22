package rssmd.studium.nhd.imeireceiver;

import android.media.AudioManager;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;


public class MainActivity extends ActionBarActivity {

    /**
     * Bit length of the confidential, here: IMEI number is maximal 50-bit long
     */
    private static final int BIT_LENGTH = 50;

    /**
     * The buffer time in which the receiving process continues after the sending process has
     * finished
     */
    private static final long BUFFER_TIME = 4000;

    /**
     * Delay until the next confidential bit is sent
     */
    private static int sendingInterval = 400;

    /**
     * Delay between two listening on the covert channel
     */
    private static int receivingInterval = 10;

    /**
     * Delay between two iteration of the waiting phase
     */
    private static int busyInterval = 10;

    private static String imeiBinary="";
    private static long imei = 0;
    private static TextView binaryText;
    private static TextView statusText;
    public static TextView statusMesText;

    /**
     * The sync time point between the two parties at which the transmission starts
     */
    private static long appointment;
    private static int counter = 1;

    /**
     * Describe the fact if the confidential is completely received or not
     */
    private static boolean isFinished = false;

    /**
     * Number of bits received so far
     */
    private static int receivedBits = 0;

    /**
     * End time point of the receiving process
     */
    private static long endTime;

    /**
     * The last observed volume level
     */
    private static int oldVolume = 0;
    private static int newVolume = 0;

    /**
     * The last time point that a change of volume level is observed
     */
    private static long oldTime = 0;
    private static long newTime = 0;

    private static int bitNum;
    private static Handler mHandler;
    static AudioManager audioMgr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mHandler = new Handler();
        audioMgr = (AudioManager)getSystemService(this.AUDIO_SERVICE);

        binaryText = (TextView)findViewById(R.id.imeiBinary);
        statusText = (TextView)findViewById(R.id.status);
        statusMesText = (TextView)findViewById(R.id.statusMes);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    //--------------------------------------------------------------------------------------------->
    //                        Phase 2 - Making Agreement on Sync Time Point - Receiver
    //<---------------------------------------------------------------------------------------------

    /**
     * Receive information about the sync time point and trigger the waiting step.
     * This method is invoked by Receiver.onReceive(Context,Intent)
     * @param appt
     *                  The sync time point that sender starts transmitting data
     */
    public static void receiveSyncMes(long appt){

        // Retrieve the sync time point
        appointment = appt;

        // Set the volume to minimal value such that the sender can set it back to maximal value
        // in order to inform the receiver that transmission is going to be started
        audioMgr.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.STREAM_MUSIC);

        // Update the GUI
        statusMesText.setText("Sync message received... Volume set to min... Waiting for sender...");

        // Busy loop until sync time point
        waitUntilAppointment.run();
    }

    /**
     * Listening task that is periodically performed until short (3000ms) before the sync time point
     * This method guarantees the synchronization between the sender and the receiver.
     */
    static Runnable waitUntilAppointment = new Runnable() {
        @Override
        public void run() {

            if(System.currentTimeMillis() >= appointment - 3000){

                // Update GUI : change status bar
                String statusStr = "";
                for(int j=0; j <= counter % 15; j++)
                    statusStr += "[] ";
                statusText.setText(statusStr);

                counter++;
                mHandler.postDelayed(waitUntilAppointment, busyInterval);

            }
            else{
                // Start preparing for listening to data
                prepareReceiving.run();
            }
        }
    };


    //--------------------------------------------------------------------------------------------->
    //                        Phase 3 - Synchronization - Receiver
    //<---------------------------------------------------------------------------------------------

    /**
     * Listening task that is periodically performed to listen on the confirmation from the
     * receiver that transmission is started in short
     *
     */
    static Runnable prepareReceiving = new Runnable() {
        @Override
        public void run() {

            if(audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC)
                    != audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC)){

                // Update GUI : change status bar
                String statusStr = "";
                for(int j=0; j <= counter % 15; j++)
                    statusStr += "[] ";
                statusText.setText(statusStr);

                counter++;
                mHandler.postDelayed(prepareReceiving, busyInterval);
            }
            else{
                // Max volume
                // => Sender has just informed that transmission is about to performed
                oldTime = System.currentTimeMillis();
                oldVolume = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

                // Compute the time needed
                endTime = System.currentTimeMillis() + sendingInterval*BIT_LENGTH;
                // Add a "buffer time"
                endTime += BUFFER_TIME;
                receiving.run();
            }
        }
    };


    //--------------------------------------------------------------------------------------------->
    //                        Phase 4 - Transmission of Data - Receiver
    //<---------------------------------------------------------------------------------------------

    /**
     * Listening task that is periodically performed to listen on the volume covert channel
     *
     */
    static Runnable receiving = new Runnable() {
        @Override
        public void run() {

            if(!isFinished){

                // Update GUI : change status bar
                String statusStr = "";
                for(int j=0; j <= counter % 15; j++)
                    statusStr += "[] ";
                statusText.setText(statusStr);

                // Observe the current volume
                newVolume = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC);

                if (newVolume != oldVolume){
                    // Volume change detected
                    // Compute the number of transmitted bits
                    newTime = System.currentTimeMillis();
                    bitNum = (int) Math.floor((newTime - oldTime) / sendingInterval);
                    if(newVolume == audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC)){
                        // Changed from 0 -> 1
                        // The transmitted bits are 0s
                        for(int i = 1; i <= bitNum; i++){
                            imeiBinary += "0";
                        }
                        receivedBits += bitNum;
                        // Update status
                        statusMesText.setText("Listening. Bit received so far: " + receivedBits);
                    }
                    else{
                        // Changed from 1 -> 0
                        // The transmitted bits are 1s
                        for(int i = 1; i <= bitNum; i++){
                            imeiBinary += "1";
                        }
                        receivedBits += bitNum;
                        // Update status
                        statusMesText.setText("Listening. Bit received so far: " + receivedBits);
                    }
                    oldTime = newTime;
                    oldVolume = newVolume;
                }

                // Time limitation has been exceeded
                // => finish receiving
                if(System.currentTimeMillis() > endTime)
                    isFinished = true;

                counter++;
                mHandler.postDelayed(receiving, receivingInterval);
            }
            else{
                // Receiving process has been now terminated
                // The last bits might not be counted in, compute them
                if(imeiBinary.length() < BIT_LENGTH){
                    if(oldVolume != audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC)){
                        // The last observed bit is 1 (or no observed bit at all!)
                        // => The remaining bits are 0s
                        for(int i = 0; i <= 3 + BIT_LENGTH - imeiBinary.length(); i++){
                            imeiBinary += "0";
                        }
                    }
                    else{
                        // The last observed bit is 0
                        // => The remaining bits are 1s
                        for(int i = 0; i <= 3 + BIT_LENGTH - imeiBinary.length(); i++){
                            imeiBinary += "1";
                        }
                    }

                }
                // Truncate unwanted bits at the end
                imeiBinary = imeiBinary.substring(0, Math.min(imeiBinary.length(), BIT_LENGTH));

                binaryText.setText("Received bit sequence:   " + imeiBinary);

                // Compute the IMEI based on the received bit sequence
                for (int i = 0; i < imeiBinary.length(); i++){
                    if(imeiBinary.charAt(imeiBinary.length() - 1 - i) == '1')
                        imei += Math.pow(2, i);
                }
                // Update status
                statusMesText.setText("Finished! Received IMEI ("+ imeiBinary.length() +" bits): " + imei);
            }
        }
    };
}