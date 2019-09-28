package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageItemInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final int SERVER_PORT = 10000;
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    static final int SOCKET_TIMEOUT = 1500;
    static final String[] ports = {REMOTE_PORT0, REMOTE_PORT1, REMOTE_PORT2, REMOTE_PORT3, REMOTE_PORT4};
    static final String ACK = "ACK";
    static final String authority = "edu.buffalo.cse.cse486586.groupmessenger2.provider";
    static final String scheme = "content";
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    static final String FAILURE = "failure";
    static final String PING = "ping";
    static final String delimiter = "~";
    public int mPriority = 0, mAvdCount;
    int mSequenceNo = 0, mMsgCount = 0;
    TextView mTextView;
    String mMyPort = "";
    public HashMap<String, ArrayList<String>> mMap;
    public PriorityQueue<Message> mPriorityQueue;
    //Socket mSocket1, mSocket2, mSocket3, mSocket4, mSocket5;
    public List<String> mLivePortsList = new ArrayList<String>();
    //public  boolean mFailureFlag = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        Collections.addAll(mLivePortsList, ports);
        mAvdCount = mLivePortsList.size();

        mMap = new HashMap<String, ArrayList<String>>();

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        mMyPort = String.valueOf((Integer.parseInt(portStr) * 2));

        /*
            References:
            1. https://docs.oracle.com/javase/7/docs/api/java/util/PriorityQueue.html
            2. https://stackoverflow.com/questions/1871253/updating-java-priorityqueue-when-its-elements-change-priority
            3. https://stackoverflow.com/questions/12917372/priority-queues-of-objects-in-java
         */
        mPriorityQueue = new PriorityQueue<Message>(5, new Comparator<Message>() {
            @Override
            public int compare(Message first, Message second) {
                return Double.compare(first.getPriority(), second.getPriority());
            }
        });

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Failed to create ServerSocket");
        }

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */

        /*
            References:
            1. TextView Android Documentation
            https://developer.android.com/reference/android/widget/TextView
         */
        mTextView = (TextView) findViewById(R.id.textView1);
        mTextView.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(mTextView, getContentResolver()));
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        /*
            References:
            1. EditText Android Documentation
            https://developer.android.com/reference/android/widget/EditText
         */
        final EditText textField = (EditText) findViewById(R.id.editText1);

        Button sendButton = (Button) findViewById(R.id.button4);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String message = textField.getText().toString() + "\n";
                textField.setText("");
                mMsgCount++;

                if (message.isEmpty())
                    return;

                for (String port : mLivePortsList) {
                    new ClientTask().executeOnExecutor(
                            AsyncTask.THREAD_POOL_EXECUTOR, message, port);
                }
            }
        });

        final Handler handler = new Handler();
        final Runnable pinger = new Runnable() {
            @Override
            public void run() {
                new MultiTask().executeOnExecutor(
                        AsyncTask.SERIAL_EXECUTOR, PING);
                handler.postDelayed(this, 8000);
            }
        };
        handler.postDelayed(pinger, 10000);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    /* References:
       1. Socket programming:
       https://docs.oracle.com/javase/tutorial/networking/sockets/index.html
       2. Two-way communication via Socket:
       https://www.youtube.com/watch?v=-xKgxqG411c
       3. Keeping socket connection alive
       https://stackoverflow.com/questions/36135983/cant-send-multiple-messages-via-socket
     */
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        String mFailedPort = "";

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            try {
                while (true) {
                    Socket socket = serverSocket.accept();
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));
                    String message = in.readLine();
                    if (message != null) {
                        String[] result = message.split(delimiter);

                        if (result[0].equals("1")) {
                            Log.d(TAG, "Message received by Server " + getClientName(mMyPort));
                            String id = result[1];
                            String text = result[2];

                            String[] ch = id.split("@");
                            if (!mFailedPort.isEmpty() && ch[1].equals(getClientName(mFailedPort)))
                                continue;

                            double priority = ++mPriority + Integer.valueOf(getClientName(mMyPort))*0.1;
                            Message msg = new Message(id, priority, text, false);
                            mPriorityQueue.add(msg);

                            PrintWriter out = new PrintWriter(socket.getOutputStream());
                            String str = "2" + delimiter + id + delimiter + priority + "\n";
                            Log.d(TAG, "Proposed Priority sent from Server " + getClientName(mMyPort) + " to Client : " + str);
                            out.write(str);
                            out.flush();

                            String log = "PQ at 1 : ";
                            for(Message ptr : mPriorityQueue) {
                                log += ptr.mId + "|" + ptr.getText() + "|" + ptr.getPriority() + "   ";
                            }
                            Log.d(TAG, log);

                        } else if (result[0].equals("3")) {
                            String id = result[1];
                            double agreedPriority = Double.valueOf(result[2]);
                            Log.d(TAG, "Agreed Proposal received by Server " + getClientName(mMyPort) + " for msg : " + id);

                            String[] ch = id.split("@");
                            if (!mFailedPort.isEmpty() && ch[1].equals(getClientName(mFailedPort)))
                                continue;

                            mPriority = Math.max(mPriority, (int) agreedPriority);

                            String ack = ACK + "\n";
                            //Log.d(TAG, "ACK sent from Server to Client");
                            PrintWriter out = new PrintWriter(socket.getOutputStream());
                            out.write(ack);
                            out.flush();


                            //handleAgreement(id, agreedPriority, mFailedPort);
                            Message temp = new Message();
                            for (Message ptr : mPriorityQueue) {
                                if (ptr!= null && ptr.getId().equals(id)) {
                                    temp = ptr;
                                    break;
                                }
                            }
                            mPriorityQueue.remove(temp);
                            temp.setPriority(agreedPriority);
                            temp.setDeliverable(true);
                            mPriorityQueue.add(temp);

                            String log = "PQ at 3 : ";
                            for(Message ptr : mPriorityQueue) {
                                log += ptr.mId + "|" + ptr.getText() + "|" + ptr.getPriority() + "   ";
                            }
                            Log.d(TAG, log);

                            publishProgress(mFailedPort.isEmpty() ? "" : getClientName(mFailedPort));
                        } else if (result[0].equals("4")) {
                            String failedPort = result[1];
                            mFailedPort = failedPort;

                            String ack = ACK + "\n";
                            PrintWriter out = new PrintWriter(socket.getOutputStream());
                            out.write(ack);
                            out.flush();

                            boolean flag = mLivePortsList.contains(failedPort);
                            if (flag)
                                publishProgress(FAILURE, failedPort);
                        } else if (result[0].equals("5")) {
                            Log.d(TAG, "Ping received");
                            if (!mFailedPort.isEmpty())
                                publishProgress(getClientName(mFailedPort));
                            String ack = ACK + "\n";
                            PrintWriter out = new PrintWriter(socket.getOutputStream());
                            out.write(ack);
                            out.flush();
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
                Log.e(TAG, "ServerTask SocketTimeoutException");
            } catch (IOException e) {
                Log.e(TAG, "ServerTask Socket IOException");
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (values[0].equals(FAILURE)) {
                handleFailure(values[1]);
            } else {
                //deliverMessages(values[0]);
                if (!values[0].isEmpty() && mPriorityQueue.peek() != null) {
                    String[] ch = mPriorityQueue.peek().getId().split("@");
                    if (ch[1].equals(values[0])) {
                        mPriorityQueue.poll();
                    }
                }
                while (mPriorityQueue.peek() != null && mPriorityQueue.peek().getDeliverable()) {
                    String log = "PQ : ";
                    for(Message ptr : mPriorityQueue) {
                        log += ptr.mId + "|" + ptr.getText() + "|" + ptr.getPriority() + "   ";
                    }
                    Log.d(TAG, log);

                    Message msg = mPriorityQueue.poll();
                    String text = msg.getText();
                    Log.d(TAG, "Final : " + msg.getId() + "  " + text + "  " + msg.getPriority());
                    mTextView.append(text + "\n");
                    updateContentProvider(text);
                }
            }
        }
    }

    /*synchronized void handleAgreement(String id, double agreedPriority, String failedPort) {
        Message temp = new Message();
        for (Message ptr : mPriorityQueue) {
            if (ptr!= null && ptr.getId().equals(id)) {
                temp = ptr;
                break;
            }
        }
        mPriorityQueue.remove(temp);
        temp.setPriority(agreedPriority);
        temp.setDeliverable(true);
        mPriorityQueue.add(temp);

        String log = "PQ at 3 : ";
        for(Message ptr : mPriorityQueue) {
            log += ptr.mId + "|" + ptr.getText() + "|" + ptr.getPriority() + "   ";
        }
        Log.d(TAG, log);

        //deliverMessages(failedPort.isEmpty() ? "" : getClientName(failedPort));
    }*/

    /*synchronized void deliverMessages(String failedPort) {
        if (!failedPort.isEmpty() && mPriorityQueue.peek() != null) {
            String[] ch = mPriorityQueue.peek().getId().split("@");
            if (ch[1].equals(failedPort)) {
                mPriorityQueue.poll();
            }
        }
        while (mPriorityQueue.peek() != null && mPriorityQueue.peek().getDeliverable()) {
            String log = "PQ : ";
            for(Message ptr : mPriorityQueue) {
                log += ptr.mId + "|" + ptr.getText() + "|" + ptr.getPriority() + "   ";
            }
            Log.d(TAG, log);

            Message msg = mPriorityQueue.poll();
            String text = msg.getText();
            Log.d(TAG, "Final : " + msg.getId() + "  " + text + "  " + msg.getPriority());
            mTextView.append(text + "\n");
            updateContentProvider(text);
        }
    }*/

    /*
        References:
        1. Content Provider Android Documentation
        https://developer.android.com/guide/topics/providers/content-providers
     */
    private void updateContentProvider(String message) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        Uri uri = uriBuilder.build();

        ContentValues cv = new ContentValues();
        cv.put(KEY_FIELD, mSequenceNo++);
        cv.put(VALUE_FIELD, message);

        getContentResolver().insert(uri, cv);
    }

    private synchronized void sendFailureMessage(String failedPort) {
        List<String> updatedPortsList = new ArrayList<String>(mLivePortsList);
        updatedPortsList.remove(failedPort);
        for (String port : updatedPortsList)
            new MultiTask().executeOnExecutor(
                    AsyncTask.THREAD_POOL_EXECUTOR, FAILURE, port, failedPort);
    }

    private void handleFailure(String failedPort) {
        mLivePortsList.remove(failedPort);
        mAvdCount = mLivePortsList.size();
        Log.d(TAG, "Live AVD Count : " + mAvdCount);

        final String failedClient = getClientName(failedPort);

        List<String> rList = new ArrayList<String>();
        for (String msgId : mMap.keySet()) {
            if (checkProposals(msgId, failedClient)) {
                rList.add(msgId);
            }
        }
        for (String str : rList) {
            mMap.remove(str);
        }

        removeStalledMessages(failedClient);
    }

    private void removeStalledMessages(String failedClient) {
        List<Message> remList = new ArrayList<Message>();
        for (Message ptr : mPriorityQueue) {
            String[] ch = ptr.getId().split("@");
            if (ch[1].equals(failedClient)) {
                remList.add(ptr);
            }
        }
        for (Message ptr : remList) {
            mPriorityQueue.remove(ptr);
        }
    }

    private synchronized boolean checkProposals(String msgId, String failedClient) {
        if (mMap.containsKey(msgId) && mMap.get(msgId).size() >= mAvdCount) {
            //String maxVal = Collections.max(mMap.get(msgId)).toString();

            List<Double> priorities = new ArrayList<Double>();
            List<String> tempList = mMap.get(msgId);
            for (String str : tempList) {
                String[] arr = str.split(delimiter);
                if (arr[0].equals(failedClient))
                    return false;
                priorities.add(Double.valueOf(arr[1]));
            }

            String maxVal = Collections.max(priorities).toString();

            for (String port : mLivePortsList) {
                new ClientAgreedTask().executeOnExecutor(
                        AsyncTask.THREAD_POOL_EXECUTOR, port, msgId, maxVal);
            }
            return true;
        }
        return false;
    }

    private class ClientTask extends AsyncTask<String, String, Void> {
        String mRemotePort = "";

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                String remotePort = msgs[1];
                mRemotePort = remotePort;
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(remotePort));
                socket.setSoTimeout(SOCKET_TIMEOUT);
                //setSocket(remotePort, socket);

                /*Socket socket = getSocket(remotePort);
                if (socket == null) return null;
                Log.d(TAG, "Socket Connected: " + socket.isConnected());
                Log.d(TAG, "Socket Closed: " + socket.isClosed());*/

                String msgToSend = msgs[0];

                String msgId = mMsgCount + "@" + getClientName(mMyPort);

                if (!mMap.containsKey(msgId)) {
                    //mMap.put(msgId, new ArrayList<Double>());
                    mMap.put(msgId, new ArrayList<String>());
                }

                PrintWriter out = new PrintWriter(socket.getOutputStream());
                String str = "1" + delimiter + msgId + delimiter + msgToSend;
                Log.d(TAG, "Message sent from Client " + getClientName(mMyPort) +
                        " to Server " + getClientName(remotePort) + " : " + str);
                out.write(str);
                out.flush();


                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                String proposed = in.readLine();
                if (proposed != null) {
                    Log.d(TAG, "Proposed priority received by Client");
                    String[] result2 = proposed.split(delimiter);
                    double proposedPriority = Double.valueOf(result2[2]);
                    if (mMap.containsKey(msgId)) {
                        //mMap.get(msgId).add(proposedPriority);
                        mMap.get(msgId).add(getClientName(remotePort) + delimiter + proposedPriority);
                    }
                    publishProgress(msgId);
                    in.close();
                    out.close();
                    socket.close();
                }

                //Log.d(TAG, "Socket Connected: " + socket.isConnected());
                //Log.d(TAG, "Socket Closed: " + socket.isClosed());
                //Log.d(TAG, "MAP SIZE : " + mMap.get(msgId).size());
                return null;

            } catch (SocketTimeoutException e) {
                Log.e(TAG, "ClientTask SocketTimeoutException" + mRemotePort);
                if (mLivePortsList.contains(mRemotePort)) {
                    publishProgress(FAILURE, mRemotePort);
                }
            } catch (StreamCorruptedException e) {
                Log.e(TAG, "ClientTask StreamCorruptedException" + mRemotePort);
                if (mLivePortsList.contains(mRemotePort)) {
                    publishProgress(FAILURE, mRemotePort);
                }
            } catch (EOFException e) {
                Log.e(TAG, "ClientTask EOFException" + mRemotePort);
                if (mLivePortsList.contains(mRemotePort)) {
                    publishProgress(FAILURE, mRemotePort);
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException" + mRemotePort);
                if (mLivePortsList.contains(mRemotePort)) {
                    publishProgress(FAILURE, mRemotePort);
                }
            } catch (IOException e) {
                Log.e(TAG, "ClientTask Socket IOException : " + mRemotePort);
                if (mLivePortsList.contains(mRemotePort)) {
                    publishProgress(FAILURE, mRemotePort);
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (values[0].equals(FAILURE)) {
                sendFailureMessage(values[1]);
            } else {
                if (checkProposals(values[0], "-1")) {
                    mMap.remove(values[0]);
                }
            }
        }
    }

    private class ClientAgreedTask extends AsyncTask<String, String, Void> {
        String mRemotePort = "";

        @Override
        protected Void doInBackground(String... strings) {
            try {
                String remotePort = strings[0];
                mRemotePort = remotePort;
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(remotePort));
                socket.setSoTimeout(SOCKET_TIMEOUT);

               /* Socket socket = getSocket(remotePort);
                if (socket == null) return null;
                Log.d(TAG, "Socket Connected: " + socket.isConnected());
                Log.d(TAG, "Socket Closed: " + socket.isClosed());*/

                String msgId = strings[1];
                String agreedPriority = strings[2];

                PrintWriter out = new PrintWriter(socket.getOutputStream());
                String str = "3" + delimiter + msgId + delimiter + agreedPriority + "\n";
                Log.d(TAG, "Agreed Proposal sent from Client " + getClientName(mMyPort) +
                        " to Server " + getClientName(remotePort) + " : " + str);
                out.write(str);
                out.flush();

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                String ack = in.readLine();
                if (ack != null && ack.equals(ACK)) {
                    Log.d(TAG, "ACK received by Client " + getClientName(mMyPort));
                    out.close();
                    socket.close();
                } else {
                    Log.d(TAG, "NULL");
                }
            } catch (SocketTimeoutException e) {
                Log.e(TAG, "ClientAgreedTask SocketTimeoutException" + mRemotePort);
                if (mLivePortsList.contains(mRemotePort)) {
                    publishProgress(FAILURE, mRemotePort);
                }
            } catch (StreamCorruptedException e) {
                Log.e(TAG, "ClientAgreedTask StreamCorruptedException" + mRemotePort);
                if (mLivePortsList.contains(mRemotePort)) {
                    publishProgress(FAILURE, mRemotePort);
                }
            } catch (EOFException e) {
                Log.e(TAG, "ClientAgreedTask EOFException" + mRemotePort);
                if (mLivePortsList.contains(mRemotePort)) {
                    publishProgress(FAILURE, mRemotePort);
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientAgreedTask UnknownHostException" + mRemotePort);
                if (mLivePortsList.contains(mRemotePort)) {
                    publishProgress(FAILURE, mRemotePort);
                }
            } catch (IOException e) {
                Log.e(TAG, "ClientAgreedTask Socket IOException : " + mRemotePort);
                if (mLivePortsList.contains(mRemotePort)) {
                    publishProgress(FAILURE, mRemotePort);
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (values[0].equals(FAILURE)) {
                sendFailureMessage(values[1]);
            }
        }
    }

    private class MultiTask extends AsyncTask<String, String, Void> {
        String mRemotePort = "";

        @Override
        protected Void doInBackground(String... strings) {
            try {
                String remotePort = "", failedPort = "", str = "";
                if (strings[0].equals(FAILURE)) {
                    remotePort = strings[1];
                    failedPort = strings[2];
                    str += "4" + delimiter + failedPort + "\n";
                    Log.d(TAG, "Failure message sent from Client " + getClientName(mMyPort) +
                            " to Server " + getClientName(remotePort) + " : " + str);
                } else if (strings[0].equals(PING)) {
                    remotePort = getPingPort(mMyPort);
                    str += "5" + delimiter + "\n";
                    Log.d(TAG, "Ping sent to : " + remotePort);
                }
                mRemotePort = remotePort;

                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(remotePort));

                PrintWriter out = new PrintWriter(socket.getOutputStream());
                out.write(str);
                out.flush();

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                String ack = in.readLine();
                if (ack != null && ack.equals(ACK)) {
                    out.close();
                    socket.close();
                    if (strings[0].equals(PING))
                        Log.d(TAG, "Ping ACK received");
                }
            } catch (SocketTimeoutException e) {
                Log.e(TAG, "MultiTask SocketTimeoutException" + mRemotePort);
                if (mLivePortsList.contains(mRemotePort)) {
                    publishProgress(FAILURE, mRemotePort);
                }
            } catch (StreamCorruptedException e) {
                Log.e(TAG, "MultiTask StreamCorruptedException" + mRemotePort);
                if (mLivePortsList.contains(mRemotePort)) {
                    publishProgress(FAILURE, mRemotePort);
                }
            } catch (EOFException e) {
                Log.e(TAG, "MultiTask EOFException" + mRemotePort);
                if (mLivePortsList.contains(mRemotePort)) {
                    publishProgress(FAILURE, mRemotePort);
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "MultiTask UnknownHostException" + mRemotePort);
                if (mLivePortsList.contains(mRemotePort)) {
                    publishProgress(FAILURE, mRemotePort);
                }
            } catch (IOException e) {
                Log.e(TAG, "MultiTask Socket IOException : " + mRemotePort);
                if (mLivePortsList.contains(mRemotePort)) {
                    publishProgress(FAILURE, mRemotePort);
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (values[0].equals(FAILURE)) {
                sendFailureMessage(values[1]);
            }
        }
    }

    //Method created for logging purposes
    private String getClientName(String portNumber) {
        switch (Integer.valueOf(portNumber)) {
            case 11108:
                return "1";
            case 11112:
                return "2";
            case 11116:
                return "3";
            case 11120:
                return "4";
            case 11124:
                return "5";
            default: return "";
        }
    }

    private String getPingPort(String portNumber) {
        /*switch (Integer.valueOf(portNumber)) {
            case 11108:
                return "11112";
            case 11112:
                return "11116";
            case 11116:
                return "11120";
            case 11120:
                return "11124";
            case 11124:
                return "11108";
            default: return "";
        }*/

        int i = mLivePortsList.indexOf(portNumber);
        if (++i >= mLivePortsList.size())
            i = 0;
        return mLivePortsList.get(i);
    }

    /*
    private void setSocket(String portNumber, Socket socket) {
        switch (Integer.valueOf(portNumber)) {
            case 11108:
                mSocket1 = socket;
            case 11112:
                mSocket2 = socket;
            case 11116:
                mSocket3 = socket;
            case 11120:
                mSocket4 = socket;
            case 11124:
                mSocket5 = socket;
            default:
        }
    }

    private Socket getSocket(String portNumber) {
        switch (Integer.valueOf(portNumber)) {
            case 11108:
                return mSocket1;
            case 11112:
                return mSocket2;
            case 11116:
                return mSocket3;
            case 11120:
                return mSocket4;
            case 11124:
                return mSocket5;
            default:
                return null;
        }
    }*/

    class Message {
        private String mId;
        private double mPriority;
        private String mText;
        private boolean mDeliverable;

        public Message() {

        }

        public Message(String id, double priority, String text, boolean deliverable) {
            mId = id;
            mPriority = priority;
            mText = text;
            mDeliverable = deliverable;
        }

        public String getId() {
            return mId;
        }

        public double getPriority() {
            return mPriority;
        }

        public String getText() {
            return mText;
        }

        public boolean getDeliverable() {
            return mDeliverable;
        }

        public void setId(String id) {
            mId = id;
        }

        public void setPriority(double priority) {
            mPriority = priority;
        }

        public void setText(String text) {
            mText = text;
        }

        public void setDeliverable(boolean deliverable) {
            mDeliverable = deliverable;
        }
    }
}
