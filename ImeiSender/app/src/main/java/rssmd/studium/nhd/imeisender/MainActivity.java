package rssmd.studium.nhd.imeisender;

import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

/**
 * @author Hoang Duong Nguyen
 */
public class MainActivity extends ActionBarActivity {

    /**
     * Bit length of the confidential, here: IMEI number is maximal 50-bit long
     */
    private final int BIT_LENGTH = 50;

    /**
     * Delay between two iterations for the start-up phase
     */
    private int startUpInterval = 750;

    /**
     * Delay between two iterations for the phase in which the sender waits until sync time point
     */
    private int waitingInterval = 10;

    /**
     * Delay until the next confidential bit is sent
     */
    private int sendingInterval = 400;

    /**
     * The time between the time point at which  sender informs the receiver that transmission is
     * about to be performed and the time point at which data begins to be transmitted. This should
     * be half of sendingInterval
     */
    private final long EARLY_INFORM = 200;

    /**
     * The sync time point between the two parties at which the transmission starts
     */
    private long appointment = 0;

    /**
     * The binary presentation of the IMEI number
     */
    private int[] imeiBinary;

    /**
     * The confidential INEI number
     */
    private long imei = 0;

    private TextView imeiText;
    private TextView binaryText;
    private TextView statusText;
    private TextView statusMesText;

    /**
     * The fact if the sender has not informed the sender that transmission is about to be performed
     */
    private boolean notYetInform = true;
    private long counter = 1;
    private int bitToSend = 0;

    /**
     * The action of the intent used for the synchronization before starting the transmission.
     * Note that this intent-based communication is legal.
     */
    private final String INTENT_ID = "rssmd.studium.nhd.SYNC_ACTION";

    /**
     * Some tablet does not have an IMEI number, so use this arbitrary number for testing purpose
     */
    private final String MY_IMEI = "28101996";

    private Handler mHandler;
    AudioManager audioMgr;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new Handler();
        audioMgr=(AudioManager)getSystemService(this.AUDIO_SERVICE);

        imeiText=(TextView)findViewById(R.id.imei);
        binaryText=(TextView)findViewById(R.id.binary);
        statusText=(TextView)findViewById(R.id.status);
        statusMesText=(TextView)findViewById(R.id.statusMes);

        // Start start-up phase
        startUp.run();
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
    //                             Phase 1  - Gathering Confidential Data
    //<---------------------------------------------------------------------------------------------

    /**
     * Start-up phase: For illustrative purpose
     */
    Runnable startUp = new Runnable() {
        @Override
        public void run() {

            if(counter <= 19){

                String statusStr;
                statusMesText.setText("Reading IMEI...");

                // Display status
                statusStr = "";
                for(int j=0; j <= counter % 10; j++)
                    statusStr += "[] ";
                statusText.setText(statusStr);

                if(counter >= 7){
                    statusMesText.setText("Convert to binary...");
                    readIMEI();
                }

                if(counter > 12){
                    statusMesText.setText("Preparing to send");

                    String binaryStr = Long.toBinaryString(imei);

                    // If the bit-representation of imei is shorter than 50 bits
                    // => make it 50-bit-long !
                    if(binaryStr.length() < BIT_LENGTH){
                        int diff = BIT_LENGTH - binaryStr.length();
                        for(int i = 1; i <= diff; i++)
                            binaryStr = "0" + binaryStr;
                    }

                    // Compute the binary array that represents IMEI
                    imeiBinary = new int[binaryStr.length()];
                    for(int i = 0; i < binaryStr.length(); i++)
                        imeiBinary[i] = Integer.parseInt(binaryStr.substring(i,i+1));

                    String tmp = "";

                    for(int i = 0; i < imeiBinary.length; i++)
                        tmp += imeiBinary[i];

                    binaryText.setText(tmp + " ("+ imeiBinary.length +" bits)");

                }
                counter++;
                mHandler.postDelayed(startUp, startUpInterval);
            }
            else
                // Start-up phase has finished
                // Now the main part begins
                triggerSendingIMEI();
        }
    };

    //--------------------------------------------------------------------------------------------->
    //                        Phase 2 - Making Agreement on Sync Time Point - Sender
    //<---------------------------------------------------------------------------------------------

    /**
     *  Determine the sync time point and inform the receiver
     */
    public void triggerSendingIMEI()  {

        // Setting up the sync time point
        appointment = System.currentTimeMillis() + 5000;
        String appointmentStr = Long.toString(appointment);

        // Pack the data into an intent and send it via Broadcast
        Intent intent = new Intent(INTENT_ID);
        intent.putExtra("rssmd.studium.nhd.Appointment", appointmentStr);

        // Inform the receiver about the appointment time point
        this.sendBroadcast(intent);

        statusMesText.setText("Synchronizing with receiver...");
        // Waiting until the sync time point
        waitToSend.run();
    }

    //--------------------------------------------------------------------------------------------->
    //                        Phase 3 - Synchronization - Sender
    //<---------------------------------------------------------------------------------------------

    /**
     * Task to be periodically performed in order to wait for the sync time point
     */
    Runnable waitToSend = new Runnable() {
        @Override
        public void run() {
            // Busy loop until short before the sync time point
            if(System.currentTimeMillis() < appointment - 30){

                if(notYetInform && System.currentTimeMillis() >= appointment - EARLY_INFORM){
                    // Shortly before the sync time point
                    // First set volume to max value in order to inform the receiver that data is
                    // going to be sent in short
                    audioMgr.setStreamVolume(AudioManager.STREAM_MUSIC, audioMgr.getStreamMaxVolume(
                            AudioManager.STREAM_MUSIC), 0);

                    // The first bit should be sent next
                    bitToSend = 0;

                    notYetInform = false;
                }
                mHandler.postDelayed(waitToSend,  waitingInterval);
            }
            else{


                // Then start sending through the covert channel
                send.run();
            }
        }
    };


    //--------------------------------------------------------------------------------------------->
    //                        Phase 4 - Transmission of Data - Sender
    //<---------------------------------------------------------------------------------------------

    /**
     * Task that repeatedly sends confidential bits to the receiver via volume covert channel
     */
    Runnable send = new Runnable() {
        @Override
        public void run() {

            if (bitToSend < imeiBinary.length){
                // Display status
                String statusStr = "";
                for(int j=0; j <= counter % 10; j++)
                    statusStr += "[] ";
                statusText.setText(statusStr);

                // Send the bitToSend-th bit of the binary IMEI
                if(imeiBinary[bitToSend] == 0)
                    // Bit 0  =>  Min volume
                    audioMgr.setStreamVolume(
                            AudioManager.STREAM_MUSIC, 0, AudioManager.STREAM_MUSIC);
                else
                    // Bit 1  => Max volume
                    audioMgr.setStreamVolume(AudioManager.STREAM_MUSIC,
                            audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);

                bitToSend++;
                counter++;

                // Update GUI
                if(bitToSend == 1)
                    statusMesText.setText(bitToSend + " Bit sent");
                else
                    statusMesText.setText(bitToSend + " Bits sent");

                mHandler.postDelayed(send, sendingInterval);
            }
            else{
                // Display notification about the successful transmission
                statusMesText.setText("IMEI ("+imeiBinary.length+" bits)sent !");
            }

        }
    };

    /**
     * Obtain the IMEI number
     */
    private void readIMEI(){

        TelephonyManager telephonyManager =
                (TelephonyManager) getSystemService(this.TELEPHONY_SERVICE);
        String imeiStr = telephonyManager.getDeviceId();

        // My Tablet does not have an IMEI number, so I use an arbitrary number :)
        if(imeiStr == null)
            imeiStr = MY_IMEI;
        // Playing purpose
        imeiStr = MY_IMEI;

        imei = Long.parseLong(imeiStr);
        imeiText.setText(imeiStr);
    }
}