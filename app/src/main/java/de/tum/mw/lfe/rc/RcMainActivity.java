package de.tum.mw.lfe.rc;

/*
MIT License

        Copyright (c) 2015-2016 Michael Krause (krause@tum.de)

        Permission is hereby granted, free of charge, to any person obtaining a copy
        of this software and associated documentation files (the "Software"), to deal
        in the Software without restriction, including without limitation the rights
        to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
        copies of the Software, and to permit persons to whom the Software is
        furnished to do so, subject to the following conditions:

        The above copyright notice and this permission notice shall be included in all
        copies or substantial portions of the Software.

        THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
        IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
        FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
        AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
        LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
        OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
        SOFTWARE.
*/

import java.io.IOException;
import java.net.InetAddress;
import java.util.Calendar;
import java.util.TimeZone;

import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class RcMainActivity extends Activity{

	private static final String TAG = "Rc.Activity";
	private static final String PREFERENCES = "rcPreferences";
	private Handler mHandler = new Handler();	
	private RcMainActivity mContext = this;
	private PowerManager.WakeLock mWakeLock;

    // Silab Package to send
    private SilabPacket mSilabPacket = new SilabPacket();

    private SilabServerRunnable mSilabServerRunnable = null;
    private Thread mSilabServerThread = null;


    public static final int SILAB_TRIAL_FAIL = 77;
    public static final int SILAB_TRIAL_DEFAULT = 0;



	public static final int DIKABLIS_EVENT_START = 0; 
	public static final int DIKABLIS_EVENT_STOP = 1; 
	public static final int DIKABLIS_EVENT = 2; 
	
    private static DikablisThread mDikablisThread = null;
    private void kickOffDikablisThread(){
        if (mDikablisThread == null){
            Log.d(TAG, "start dikablis thread");
            mDikablisThread = new DikablisThread(this, mDikablisCallbackHandler, mDikablisGuiHandler);
            mDikablisThread.start();
        }
    }

    //-------------------------------------------------------------------
    private Runnable bgBgWhite = new Runnable() {
		@Override
		public void run() {
			RelativeLayout relLayout = (RelativeLayout)findViewById(R.id.relLayout);
			relLayout.setBackgroundColor(0xffffffff);
		}
	};			
    
    private Runnable bgBgGreenOn = new Runnable() {
		@Override
		public void run() {
			RelativeLayout relLayout = (RelativeLayout)findViewById(R.id.relLayout);
			relLayout.setBackgroundColor(0xffaaffaa);
		}
	};




    //-------------------------------------------------------------------
    final Handler mDikablisGuiHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            RadioButton connectedRB = (RadioButton)findViewById(R.id.dikablisConnectedRB);

            if(msg.what==SilabServerRunnable.NOT_CONNECTED){
                connectedRB.setChecked(false);
                Log.i(TAG,">>>> not connected");
                toasting("Dikablis NOT connected (e.g. closed).",2000);
            }
            if(msg.what==SilabServerRunnable.CONNECTED){
                connectedRB.setChecked(true);
                Log.i(TAG,">>>>connected");
                toasting("Dikablis connected", 2000);
            }
            if(msg.what==SilabServerRunnable.UPDATE_MARKER_TEXT){
                //String temp = new String(Byte.toString(mButton));//convert char to string
                //connectedRB.setText(temp);

            }
            TextView ip = (TextView)findViewById(R.id.ipTv);
            ip.setText(mSilabServerRunnable.ipStatus());

        }
    } ;


    //-------------------------------------------------------------------
    long mLastDiscardMessage;
    final Handler mSilabGuiHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            RadioButton connectedRB = (RadioButton)findViewById(R.id.silabConnectedRB);

            if(msg.what==SilabServerRunnable.NOT_CONNECTED){
                connectedRB.setChecked(false);
                Log.i(TAG,">>>> not connected");
                toasting("SILAB NOT connected (e.g. closed).",2000);
            }
            if(msg.what==SilabServerRunnable.CONNECTED){
                connectedRB.setChecked(true);
                Log.i(TAG,">>>>connected");
                toasting("SILAB connected", 2000);
            }
            if(msg.what==SilabServerRunnable.UPDATE_MARKER_TEXT){
                //String temp = new String(Byte.toString(mButton));//convert char to string
                //connectedRB.setText(temp);

            }
            if(msg.what==SilabServerRunnable.LOSS_OF_INFORMATION){
               long now = Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis();
               if(mLastDiscardMessage - now > 2000){toasting("discarded SILAB package",100);}
               mLastDiscardMessage = now;
               Log.e(TAG,"discarded SILAB package");

            }
            if (mSilabServerRunnable != null) {
                TextView ip = (TextView) findViewById(R.id.ipTv);
                if (ip != null) {
                    ip.setText(mSilabServerRunnable.ipStatus());
                }
            }
        }
    } ;


    //----SILAB--------
    public void startSilabServer(){
        if (mSilabServerRunnable == null){
            mSilabServerRunnable = new SilabServerRunnable(mSilabGuiHandler);
        }
        if (mSilabServerThread == null){
            mSilabServerThread = new Thread(mSilabServerRunnable);
            mSilabServerThread.start();
        }

        //TextView ip = (TextView)findViewById(R.id.ipTv);
        //ip.setText(mSilabServerRunnable.ipStatus());
    }

    public void stopSilabServer(){
        try {
            if (mSilabServerThread != null) mSilabServerThread.interrupt();
            if (mSilabServerRunnable != null) mSilabServerRunnable.closeSockets();
        } catch (Exception e) {
            Log.e(TAG, "mServerThread.interrupt() failed: " + e.getMessage());
        }
        mSilabServerRunnable = null;//new
        mSilabServerThread = null;//new
    }


    @Override
    protected void onStop() {
        super.onStop();
        
        stopSilabServer();
    }		
       
   	@Override
   	public void onDestroy() {
           super.onDestroy();	
           
		   TextView ipTv = (TextView)findViewById(R.id.dikablisIp);	
		   ipTv.removeTextChangedListener(textWatcher);

           if(mWakeLock != null){
             	mWakeLock.release();
            }
  		
  	}


   	public TextWatcher textWatcher = new TextWatcher(){
        public void afterTextChanged(Editable s) {
			TextView dikablisIp = (TextView)findViewById(R.id.dikablisIp);
			if ((dikablisIp != null) && (mDikablisThread != null)){
	        	String ip = dikablisIp.getText().toString();
		        mDikablisThread.setIp(ip);
				toasting(ip,300);

			}	
        }
        public void beforeTextChanged(CharSequence s, int start, int count, int after){}
        public void onTextChanged(CharSequence s, int start, int before, int count){


        }

    };	       
	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
	    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        //no title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //full screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,  WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
        //full light
        android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS, 255); 
	 				
		
		setContentView(R.layout.activity_main);
		//Helpers.onActivityCreateSetLayout(this);
		
		
		//load from preferences
        SharedPreferences settings = mContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);

        String ip = settings.getString("dikablisIp", "10.152.238.78");
		TextView ipTv = (TextView)findViewById(R.id.dikablisIp);	
		ipTv.setText(ip);
		kickOffDikablisThread();
        mDikablisThread.setIp(ip);
		ipTv.addTextChangedListener(textWatcher);

        Spinner s = (Spinner)findViewById(R.id.taskS);
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1,
                                       int arg2, long arg3) {
                // TODO Auto-generated method stub

                Spinner s = (Spinner)findViewById(R.id.taskS);
                String selCat = s.getItemAtPosition(arg2).toString();

                toasting("Selected Trail1",1000);


                RadioGroup trialRG = (RadioGroup)findViewById(R.id.trialRG);
                trialRG.check(R.id.trial1RB);


            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub

            }
        });



		    getWakeLock();

	}
/*
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		
			
		// Inflate the menu; this adds items to the action bar if it is present.
		//getMenuInflater().inflate(R.menu.main, menu);
		 menu.add(Menu.NONE, LAYOUT_MAIN, Menu.NONE, "Main");
		 menu.add(Menu.NONE, LAYOUT_1, Menu.NONE, "S10 M3");
		 menu.add(Menu.NONE, LAYOUT_2, Menu.NONE, "S10 M5");
		 menu.add(Menu.NONE, LAYOUT_3, Menu.NONE, "S10 M10");
		 menu.add(Menu.NONE, LAYOUT_4, Menu.NONE, "S15 M3");
		 menu.add(Menu.NONE, LAYOUT_5, Menu.NONE, "S15 M5");
		 menu.add(Menu.NONE, LAYOUT_6, Menu.NONE, "S15 M10");
		 menu.add(Menu.NONE, LAYOUT_7, Menu.NONE, "S20 M3");
		 menu.add(Menu.NONE, LAYOUT_8, Menu.NONE, "S20 M5");
		 menu.add(Menu.NONE, LAYOUT_9, Menu.NONE, "S20 M10");
		
		return true;
	}
*/

	/*
   @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {

        switch(item.getItemId())
        {
        case LAYOUT_MAIN:
        	changeToLayout(LAYOUT_MAIN);
        	return true;  
        case LAYOUT_1:
        case LAYOUT_2:
        case LAYOUT_3:
        case LAYOUT_4:
        case LAYOUT_5:
        case LAYOUT_6:
        case LAYOUT_7:
        case LAYOUT_8:
        case LAYOUT_9:
        	changeToLayout(item.getItemId());
        	return true;  
        default:
            return super.onOptionsItemSelected(item);
        }

    }	
  */

	@Override
	public void onResume() {
       super.onResume();


        startSilabServer();
       

       kickOffDikablisThread();

        TextView ip = (TextView)findViewById(R.id.ipTv);
        ip.setText(mSilabServerRunnable.ipStatus());

	}


    public void onClickBegin(View v){
        Spinner s = (Spinner)findViewById(R.id.taskS);

        byte task = (byte)(s.getSelectedItemPosition() +1);//+1 important!
        RadioGroup trialRG = (RadioGroup)findViewById(R.id.trialRG);
        byte trial = (byte)(trialRG.indexOfChild(findViewById(trialRG.getCheckedRadioButtonId())) + 1);//+1 important!


        toasting("Begin Task " +Byte.toString(task) + "-- Trial " +Byte.toString(trial) ,1000);

        if (mDikablisThread != null){
            mDikablisThread.sendDikablisTrigger(DIKABLIS_EVENT_START, (byte)1, task, (byte)trial, (byte)0);
        }

        mSilabPacket.task = task;
        mSilabPacket.trial = trial;
        if (mSilabServerRunnable != null){
            mSilabServerRunnable.send2Silab(mSilabPacket);
        }

    }
    public void onClickEnd(View v){
        Spinner s = (Spinner)findViewById(R.id.taskS);

        byte task = (byte)(s.getSelectedItemPosition() +1);//+1 important!
        RadioGroup trialRG = (RadioGroup)findViewById(R.id.trialRG);
        byte trial = (byte)(trialRG.indexOfChild(findViewById(trialRG.getCheckedRadioButtonId())) + 1);//+1 important!


        toasting("End Task " +Byte.toString(task) + "-- Trial " +Byte.toString(trial) ,1000);

        if (mDikablisThread != null){
            mDikablisThread.sendDikablisTrigger(DIKABLIS_EVENT_STOP, (byte)1, task, (byte)trial, (byte)0);
        }

        mSilabPacket.task = task;
        mSilabPacket.trial = SILAB_TRIAL_DEFAULT;
        if (mSilabServerRunnable != null){
            mSilabServerRunnable.send2Silab(mSilabPacket);
        }
    }


    public void onClickFail(View v){
        Spinner s = (Spinner)findViewById(R.id.taskS);

        byte task = (byte)(s.getSelectedItemPosition() +1);//+1 important!
        RadioGroup trialRG = (RadioGroup)findViewById(R.id.trialRG);
        byte trial = (byte)(trialRG.indexOfChild(findViewById(trialRG.getCheckedRadioButtonId())) + 1);//+1 important!


        toasting("Fail Task " +Byte.toString(task) + "-- Trial " +Byte.toString(trial) ,1000);

        if (mDikablisThread != null){
            mDikablisThread.sendDikablisTrigger(DIKABLIS_EVENT, (byte)77, task, (byte)trial, (byte)0);
        }

        mSilabPacket.task = task;
        mSilabPacket.trial = SILAB_TRIAL_FAIL;
        if (mSilabServerRunnable != null){
            mSilabServerRunnable.send2Silab(mSilabPacket);
        }

    }


    @Override
	public void onPause() {
       super.onPause();
       
       saveToPrefs();

	    if (mDikablisThread != null){
	    	mDikablisThread.end();
	    	mDikablisThread = null;
	    }      
       
	}   
   
	private void saveToPrefs(){
			//save changes to app preferences
		    SharedPreferences settings = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
		    SharedPreferences.Editor editor = settings.edit();
            if (mDikablisThread != null){
                editor.putString("dikablisIp", mDikablisThread.getIp());
            }
		    editor.commit();
	}

	private void toasting(final String msg, final int duration){
		Context context = getApplicationContext();
		CharSequence text = msg;
		Toast toast = Toast.makeText(context, text, duration);
        toast.setDuration(duration);
		toast.show();		
	}	
	
   protected void getWakeLock(){
	    try{
			PowerManager powerManger = (PowerManager) getSystemService(Context.POWER_SERVICE);
	        mWakeLock = powerManger.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP|PowerManager.FULL_WAKE_LOCK, "de.tum.ergonomie.buttons");
	        mWakeLock.acquire();
		}catch(Exception e){
       	Log.e(TAG,"get wakelock failed:"+ e.getMessage());
		}	
   }



InetAddress getBroadcastAddress() throws IOException {
    WifiManager wifi = (WifiManager)getSystemService(Context.WIFI_SERVICE);
    DhcpInfo dhcp = wifi.getDhcpInfo();
    // handle null somehow

    int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
    byte[] quads = new byte[4];
    for (int k = 0; k < 4; k++)
      quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
    return InetAddress.getByAddress(quads);
}


    private Handler mDikablisCallbackHandler = new Handler() {
        public void handleMessage(Message msg) {
            // Act on the message
            // connect to IP/port
            int dikablisFrame;
            if (msg.what == DikablisThread.DIKABLIS_FRAMENUMBER){
                String temp = (String)msg.obj;
                try{
                    dikablisFrame = Integer.parseInt(temp);
                }
                catch(Exception ex){
                    dikablisFrame = -1;
                    Log.e(TAG, "Failed to convert Dikablis frameCount: "+ex.getMessage());
                }
                mSilabPacket.dikablisFrame = dikablisFrame;
                if (mSilabServerRunnable != null){
                    mSilabServerRunnable.send2Silab(mSilabPacket);
                }
            }

        }
    };




private String getVersionString(){
	String retString = "";
	String appVersionName = "";
	int appVersionCode = 0;
	try{
		appVersionName = getPackageManager().getPackageInfo(getPackageName(), 0 ).versionName;
		appVersionCode= getPackageManager().getPackageInfo(getPackageName(), 0 ).versionCode;
	}catch (Exception e) {
		Log.e(TAG, "getVersionString failed: "+e.getMessage());
	 }
	
	retString = "V"+appVersionName+"."+appVersionCode;
	
	return retString;
}		   



	


}
