package com.example.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.AMQP.BasicProperties;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


//sensor
@SuppressLint("NewApi")
public class MainActivity extends Activity implements SensorEventListener{
	
	private String ip;	
	
	// Sensor manager
		private SensorManager mSensorManager;
		private TextView mOutput;
		private static int mCurrentOrientation; // degree
		private static int mStartOrientation;
		private int mPrevOrientation;
		private static boolean mCWRotationStarted = false;
		private static boolean mCCWRotationStarted = false;
		static int mRotationAngle;
		
		private Button connect_rabbit;
		
		//bluetooth
		static final int reqeust_code_bluetooth = 8;
		// Intent request codes
	    private static final int REQUEST_CONNECT_DEVICE = 1;
	    private static final int REQUEST_ENABLE_BT = 2;
	    
	    // 	Program variables
	    private boolean connectStat = false;
	    private Button connect_button;
	    protected static final int MOVE_TIME = 80;
	    private View controlView;
	    OnClickListener myClickListener;
	    ProgressDialog myProgressDialog;
	    private Toast failToast;
	    private Handler mHandler;
	    
		// Bluetooth Stuff
	    private BluetoothAdapter mBluetoothAdapter=null;
	    private BluetoothSocket btSocket = null;
	    private BluetoothDevice Device;
	    static OutputStream outStream;
	    InputStream inStream;
	    private ConnectThread mConnectThread = null;
	    private String deviceAddress = null;
	    // Well known SPP UUID (will *probably* map to RFCOMM channel 1 (default) if not in use); 
	    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
		private static final String LOG_TAG = null;
		
		Thread workerThread;
	    byte[] readBuffer;
	    int readBufferPosition;
	    int counter;
	    volatile boolean stopWorker;
	    
	    static String data1;
	    int data2;
	    
	    //private SensorManager sensorManager;
		//private Sensor sensor;
	    
	    
		
		private static final String RPC_QUEUE_NAME = "rpc_queue";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		
		
		// Create main button view
	   	 LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	   	 controlView = inflater.inflate(R.layout.activity_main, null);
	   	 controlView.setKeepScreenOn(true);
	   	 setContentView(controlView);
	   	 
	   	connect_button = (Button) findViewById(R.id.connect_button);
	   	
	   	connect_rabbit = (Button) findViewById(R.id.connect_rabbit);
	   	
		mOutput =  (TextView) findViewById(R.id.output);
		 //connectrabbit();
		
		//sensor
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    
    myProgressDialog = new ProgressDialog(this);
    failToast = Toast.makeText(this, R.string.failedToConnect, Toast.LENGTH_SHORT);
    
    mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
       	 if (myProgressDialog.isShowing()) {
            	myProgressDialog.dismiss();
            }
       	 
       	 // Check if bluetooth connection was made to selected device
            if (msg.what == 1) {
            	// Set button to display current status
                connectStat = true;
                connect_button.setText(R.string.connected);
                
                //oct. 18
                beginListenForData();
                
    	 		// Reset the BluCar
    	 //		AttinyOut = 0;
    	// 		ledStat = false;
    	 //		write(AttinyOut);
            }else {
            	// Connection failed
           	 failToast.show();
            }
        }
    };
    

    
    // Check whether bluetooth adapter exists
    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter(); 
    if (mBluetoothAdapter == null) { 
         Toast.makeText(this, R.string.no_bt_device, Toast.LENGTH_LONG).show(); 
         finish(); 
         return; 
    } 
    
	    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter(); 
	    if (mBluetoothAdapter == null) { 
	         Toast.makeText(this, R.string.no_bt_device, Toast.LENGTH_LONG).show(); 
	         finish(); 
	         return; 
	    } 
   
    // If BT is not on, request that it be enabled.
    if (!mBluetoothAdapter.isEnabled()) {
        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        
        
    }
              
    // Connect to Bluetooth Module
    connect_button.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
			if (connectStat) {
				// Attempt to disconnect from the device
				disconnect();
			}else{
				// Attempt to connect to the device
				connect();
			}
			}
		});
    
    connect_rabbit.setOnClickListener(new View.OnClickListener()
    {
        public void onClick(View v)
        {
       	 connectrabbit();
        }
    });
}
	
	//sensor
	@Override
	public void onSensorChanged(SensorEvent event) {
		float rotationVector[] = new float[16];
		float orientation[] = new float[3];

		//int cushion = (int) (mRotationAngle*0.15);
		
		int cushion;
		if(mRotationAngle >= 15){
			cushion = (int) (mRotationAngle*0.15);
		}
		else{
			cushion = (int) (mRotationAngle*0.50);
		}
		
		
		if(event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) { // Sensor.TYPE_ORIENTATION) {
			SensorManager.getRotationMatrixFromVector(rotationVector, event.values);
			SensorManager.getOrientation(rotationVector, orientation);
	  
			// orientation range: -pi ~ pi (radian)
			// orientation[0]: azimuth
			// orientation[1]: pitch
			// orientation[2]: roll
 
			double degree = (orientation[0]+Math.PI)*360/(2*Math.PI);
			mCurrentOrientation = (int)degree; 

			if(mCWRotationStarted == true) {
				//int endOrientation = mStartOrientation+mRotationAngle;
				int endOrientation = mStartOrientation+mRotationAngle-cushion;
				int delta = 0;
				
				if(endOrientation < 360) {
					//if(mCurrentOrientation+cushion >= endOrientation) {
					if(mCurrentOrientation >= endOrientation) {
						try {
							outStream.write('E');
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						mCWRotationStarted = false;
			        }
				}
				else {
					delta = endOrientation - 359;
					//delta = (int) (delta*1.05); // make air cushion 
					//if((mCurrentOrientation+cushion < mRotationAngle) && (mCurrentOrientation > delta)) {
					//if((mCurrentOrientation < mRotationAngle) && (mCurrentOrientation+cushion > delta)) {
					if((mCurrentOrientation < mRotationAngle) && (mCurrentOrientation > delta)) {
							try {
							outStream.write('E');
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						mCWRotationStarted = false;
			        }
				}
				
				String debug = "curr:"+mCurrentOrientation+",end:"+endOrientation+",delta:"+delta;
		        //Log.d(TAG, debug);
				
		        //if((mCurrentOrientation-delta) >= (endOrientation-delta)) {
				//	sendMessage("2");
				//	mCWRotationStarted = false;
		        //}
			}
			else if(mCCWRotationStarted == true) {
				int endOrientation = mStartOrientation-mRotationAngle;
				int delta = 0;
				
				if(endOrientation > 0) {
					if(mCurrentOrientation-cushion <= endOrientation) {
						try {
							outStream.write('E');
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						mCCWRotationStarted = false;
			        }
				}
				else {
					delta = endOrientation + 359;
					//delta = (int) (delta*1.05); // make air cushion 
					if((mCurrentOrientation-cushion > (360-mRotationAngle)) && (mCurrentOrientation < delta)) {
						try {
							outStream.write('E');
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						mCCWRotationStarted = false;
			        }
				}
				
				String debug = "curr:"+mCurrentOrientation+",end:"+endOrientation+",delta:"+delta;
		        //Log.d(TAG, debug);
				
		        //if((mCurrentOrientation-delta) >= (endOrientation-delta)) {
				//	sendMessage("2");
				//	mCWRotationStarted = false;
		        //}
			}
			
			String text = String.valueOf(mCurrentOrientation);
			//String text = String.valueOf(event.values[0]);
			mOutput.setText(text);
			if(false) Log.d("TestRotation:", text);
			
			mPrevOrientation = mCurrentOrientation;
		}
	}
	
	//sensor
	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub
		
	}
	
	//sensor
	@Override
	protected void onPause() {
		super.onPause();
		
      // if(D) Log.e(TAG, "- ON PAUSE -");
       
		// unregister listener
       mSensorManager.unregisterListener(this);
	}
	
	//sensor
	@Override
   public synchronized void onResume() {
       super.onResume();
       //if(D) Log.e(TAG, "+ ON RESUME +");
       
		// register this class as a listener for the orientation
		mSensorManager.registerListener(this,
		    		mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
		            //mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
		    		SensorManager.SENSOR_DELAY_FASTEST);
		
   }
	
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent){
		super.onActivityResult(requestCode, resultCode, intent);
		
		Log.e("Example", "ResultCode = " + String.valueOf(resultCode));
		
		switch (requestCode){
		case REQUEST_CONNECT_DEVICE:
			 if (resultCode == Activity.RESULT_OK) {
            	// Show please wait dialog
 				myProgressDialog = ProgressDialog.show(this, getResources().getString(R.string.pleaseWait), getResources().getString(R.string.makingConnectionString), true);
 				
            	// Get the device MAC address
        		deviceAddress = intent.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        		// Connect to device with specified MAC address
                mConnectThread = new ConnectThread(deviceAddress);
                mConnectThread.start();
                
             }else {
            	 // Failure retrieving MAC address
            	 Toast.makeText(this, R.string.macFailed, Toast.LENGTH_SHORT).show();
             }
			break;
			
		}
	}
	
   public void finishDialogNoBluetooth() {
       AlertDialog.Builder builder = new AlertDialog.Builder(this);
       builder.setMessage(R.string.alert_dialog_no_bt)
       .setIcon(android.R.drawable.ic_dialog_info)
       .setTitle(R.string.app_name)
       .setCancelable( false )
       .setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog, int id) {
                      finish();            	
               	   }
              });
       AlertDialog alert = builder.create();
       alert.show(); 
   }

	/** Thread used to connect to a specified Bluetooth Device */
    public class ConnectThread extends Thread {
   	private String address;
   	private boolean connectionStatus;
   	
		ConnectThread(String MACaddress) {
			address = MACaddress;
			connectionStatus = true;
   	}
		
		public void run() {
   		// When this returns, it will 'know' about the server, 
           // via it's MAC address. 
			try {
				BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
				
				// We need two things before we can successfully connect 
	            // (authentication issues aside): a MAC address, which we 
	            // already have, and an RFCOMM channel. 
	            // Because RFCOMM channels (aka ports) are limited in 
	            // number, Android doesn't allow you to use them directly; 
	            // instead you request a RFCOMM mapping based on a service 
	            // ID. In our case, we will use the well-known SPP Service 
	            // ID. This ID is in UUID (GUID to you Microsofties) 
	            // format. Given the UUID, Android will handle the 
	            // mapping for you. Generally, this will return RFCOMM 1, 
	            // but not always; it depends what other BlueTooth services 
	            // are in use on your Android device. 
	            try { 
	                 btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID); 
	            } catch (IOException e) { 
	            	connectionStatus = false;
	            } 
			}catch (IllegalArgumentException e) {
				connectionStatus = false;
			}
           
           // Discovery may be going on, e.g., if you're running a 
           // 'scan for devices' search from your handset's Bluetooth 
           // settings, so we call cancelDiscovery(). It doesn't hurt 
           // to call it, but it might hurt not to... discovery is a 
           // heavyweight process; you don't want it in progress when 
           // a connection attempt is made. 
           mBluetoothAdapter.cancelDiscovery(); 
           
           // Blocking connect, for a simple client nothing else can 
           // happen until a successful connection is made, so we 
           // don't care if it blocks. 
           try {
                btSocket.connect(); 
           } catch (IOException e1) {
                try {
                     btSocket.close(); 
                } catch (IOException e2) {
                }
           }
           
           // Create a data stream so we can talk to server. 
           try { 
           	outStream = btSocket.getOutputStream(); 
           	inStream = btSocket.getInputStream();
           	//read();
           	//beginListenForData();
           } catch (IOException e2) {
           	connectionStatus = false;
           }
           
           // Send final result
           if (connectionStatus) {
           	mHandler.sendEmptyMessage(1);
           }else {
           	mHandler.sendEmptyMessage(0);
           }
		}
    }
    
  //Oct. 18
    void beginListenForData()
	    {
	        final Handler handler = new Handler(); 
	        final byte delimiter = 10; //This is the ASCII code for a newline character

	        stopWorker = false;
	        readBufferPosition = 0;
	        readBuffer = new byte[1024];
	        workerThread = new Thread(new Runnable()
	        {
	            public void run()
	            {                
	               while(!Thread.currentThread().isInterrupted() && !stopWorker)
	               {
	                    try 
	                    {
	                        int bytesAvailable = inStream.available();                        
	                        if(bytesAvailable > 0)
	                        {
	                            byte[] packetBytes = new byte[bytesAvailable];
	                            inStream.read(packetBytes);
	                            for(int i=0;i<bytesAvailable;i++)
	                            {
	                                byte b = packetBytes[i];
	                                if(b == delimiter)
	                                {
	                                	int bufferPos;
	                                	bufferPos = (readBufferPosition > 0) ? readBufferPosition-1 : 0;
	                                		
	                                    byte[] encodedBytes = new byte[bufferPos];//readBufferPosition];
	                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
	                                    final String data = new String(encodedBytes, "US-ASCII"); 
	                                    readBufferPosition = 0;

	                                    handler.post(new Runnable()
	                                    {
	                                        public void run()
	                                        {
	                                        	Log.d("jr",data);
	                                        	data1 = data;
	                                        	
	                                        	/*
	                                        	if(data.charAt(0) == 'A') 
	                                        	{
	                                        		data1 = 900;
	                                        		Log.e("jkwon", "Success");
	                                        		
	                                        	}
	                                        	else
	                                        	{
	                                        		data1 = Integer.parseInt(data);
	                                        		state.setText(data);
	                                        	}*/
	                                        }
	                                    });
	                                }
	                                else
	                                {
	                                    readBuffer[readBufferPosition++] = b;
	                                }
	                            }
	                        }
	                    } 
	                    catch (IOException e2) 
	                    {
	                        stopWorker = true;
	                    }
	               }
	            }
	        });

	        workerThread.start();
	    }
    // Call this from the main Activity to send data to the remote device
    
    public void write(byte bs) {
   	 if (outStream != null) {
            try {
           	 outStream.write(bs);
            } catch (IOException e) {
            }
        }
    }
    // Call this from the main Activity to send data to the remote device
    
    public void write(byte[] bs) {
   	 if (outStream != null) {
            try {
           	 outStream.write(bs);
            } catch (IOException e) {
            }
        }
    }

    public void emptyOutStream() {
   	 if (outStream != null) {
            try {
           	 outStream.flush();
            } catch (IOException e2) {
            }
        }
    }
    
    public void connect() {
   	 // Launch the DeviceListActivity to see devices and do scan
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }
    
    public void disconnect() {
   	 if (outStream != null) {
   		 try {
   		        stopWorker = true;
   		        inStream.close();
   	 			outStream.close();
   	 			connectStat = false;
   	 			btSocket.close();
   				connect_button.setText(R.string.disconnected);
   	 		} catch (IOException e2) {
   	 		}
   	 } 
    }
	
	
	private void connectrabbit()
   {
        new rabbittest().execute(null,null,null);
   }
	
	
	private static String fib(String[] n) {
		float distance;
		float sr;
		float theta;
		data1 = null;
		if (n[0].equals("0"))
			{
				try 
               {
                   sendData();
               }
               catch (IOException ex) 
               {}
				
				while(data1 == null)
				{}

				return data1;
			}

		else if (n[0].equals("1"))
			{
			//Log.d("jr", "send data 1");
			  final List<String> list =  new ArrayList<String>();
		      Collections.addAll(list, n); 
		      list.remove(n[0]);
		      n = list.toArray(new String[list.size()]);
		      if( n[0].equals(n[2]))
		      {
	          	try {
					sendData0(n);
				}
	          	catch (IOException e) {}
	            
				while(data1 ==null)
				{}
		      }
		      else if( !n[0].equals(n[2]))
		      {
		    	  if(n[0].equals("-1"))
		    	  {
		    		  distance = Float.valueOf(n[1]);
		    		  sr = (float) (distance * Math.PI * 0.06);
		    		  theta = (float) (sr / 0.180);
		    		  theta = (float) ((theta * 180) / Math.PI);
		    		  mRotationAngle = (int)(Math.round(theta));
		    		  mStartOrientation = mCurrentOrientation;
		    		  String a =String.valueOf(mStartOrientation);
		    		  Log.d("jr",a);
		    		  mCCWRotationStarted = true;
		    		  try {
							sendData1(n);
						}
			          	catch (IOException e) {}
		    		  while(data1 ==null)
						{}
		    	  }
		    	  else if(n[2].equals("-1"))
		    	  {
		    		  distance = Float.valueOf(n[1]);
		    		  sr = (float) (distance * Math.PI * 0.06);
		    		  theta = (float) (sr / 0.180);
		    		  theta = (float) ((theta * 180) / Math.PI);
		    		  mRotationAngle = (int)(Math.round(theta));
		    		  mStartOrientation = mCurrentOrientation;
		    		  mCWRotationStarted = true;
		    		  String a =String.valueOf(mStartOrientation);
		    		  Log.d("jr",a); 
		    		  try {
							sendData1(n);
						}
			          	catch (IOException e) {}
		    		  while(data1 ==null)
						{}
		    	  }
		      }

				return "Drive done";
			}
		//avoid 2014/11/20 {
		
		else if (n[0].equals("2"))
		{
			final List<String> list =  new ArrayList<String>();
		      Collections.addAll(list, n); 
		      list.remove(n[0]);
		      n = list.toArray(new String[list.size()]);
		      
		      try {
					sendData2(n);
				}
	          	catch (IOException e) {}
	            
				while(data1 ==null)
				{}
				
				return data1;
		}
		//avoid 2014/11/20 }
		
		else
			{return "Error";}
	}
	
	/*
	 private static String fib(String[] n) {
		data1 = null;
		if (n[0].equals("0"))
			{
				try 
               {
                   sendData();
               }
               catch (IOException ex) 
               {}
				
				while(data1 == null)
				{}

				return data1;
			}

		else if (n[0].equals("1"))
			{
			//Log.d("jr", "send data 1");
			  final List<String> list =  new ArrayList<String>();
		      Collections.addAll(list, n); 
		      list.remove(n[0]);
		      n = list.toArray(new String[list.size()]);
	          	try {
					sendData0(n);
				}
	          	catch (IOException e) {}
	            
				while(data1 ==null)
				{}
		      

				return "Drive done";
			}
		
		else
			{return "Error";}
	}
	 */

	private class rabbittest extends AsyncTask<Void, Void, Void>{
		
		

		@Override
		protected Void doInBackground(Void... params) {
			final EditText myEditField = (EditText) findViewById(R.id.editText1);
			ip = myEditField.getText().toString();
			
	    	
				Connection connection = null;
				Channel channel = null;

				try {
					ConnectionFactory factory = new ConnectionFactory();
					factory.setUsername("bbb");
					factory.setPassword("123123");
					//factory.setHost("192.168.0.121");
					Log.d("jr", ip);
					factory.setHost(ip);
					//factory.setHost("192.168.1.67");
					
					connection = factory.newConnection();
					channel = connection.createChannel();
					
					channel.queueDeclare(RPC_QUEUE_NAME, false, false, false, null);
					
					channel.basicQos(1);
					
					QueueingConsumer consumer = new QueueingConsumer(channel);
					channel.basicConsume(RPC_QUEUE_NAME, false, consumer);
					
					//mOutput.append("\n"+"Waiting for messages.");
					Log.d("jr", "waiting for messages.");
					
					while (true) {
						String response = null;
						
						QueueingConsumer.Delivery delivery = consumer.nextDelivery();
						
						BasicProperties props = delivery.getProperties();
						BasicProperties replyProps = new BasicProperties
								.Builder()
								.correlationId(props.getCorrelationId())
								.build();
						
						try {
							String message = new String(delivery.getBody(),"UTF-8");
							//int n = Integer.parseInt(message);
							String[] n = message.split(" ");
							//mOutput.append("\n"+message);
							Log.d("jr", message);
							response = "" + fib(n);
						}
						catch (Exception e){
							response = "";
							//mOutput.append("[.]"+ e.toString());
							Log.e("jr", e.toString());
						}
						finally{
							channel.basicPublish("", props.getReplyTo(),  replyProps,  response.getBytes("UTF-8"));
						
							channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
						}
					}
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				finally {
					if (connection != null) {
						try{
							connection.close();
						}
						catch (Exception ignore) {}
					}
				}
			return null;
		}
	}
	

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}	
	
	static void sendData() throws IOException
    {

        outStream.write('A');
    }
	/*
	static void sendData0() throws IOException
    {

        outStream.write('B');
    }*/
	
	static void sendData0(String[] n) throws IOException
    {
		//Arrays.toString(n);
		//String str = String.valueOf(n);
		String asString = Arrays.toString(n);
		asString = asString.replace(",", "");
		asString = asString.replace("[", "");
		asString = asString.replace("]", "");
		String otherString = asString + "\n";
		byte[] byteData = otherString.getBytes();
        outStream.write('B');
        outStream.write(byteData);
        
    }
	
	static void sendData1(String[] n) throws IOException
    {
		String asString = Arrays.toString(n);
		asString = asString.replace(",", "");
		asString = asString.replace("[", "");
		asString = asString.replace("]", "");
		String otherString = asString + "\n";
		byte[] byteData = otherString.getBytes();
        outStream.write('D'); 
        outStream.write(byteData);
    }
	
	//avoid 2014/11/20
	
	static void sendData2(String[] n) throws IOException
    {
		String asString = Arrays.toString(n);
		asString = asString.replace(",", "");
		asString = asString.replace("[", "");
		asString = asString.replace("]", "");
		String otherString = asString + "\n";
		byte[] byteData = otherString.getBytes();
        outStream.write('F'); 
        outStream.write(byteData);
    }


}
