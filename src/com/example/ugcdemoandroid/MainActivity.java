package com.example.ugcdemoandroid;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.bfcloud.publish.*;
import bf.cloud.android.playutils.VodPlayer;
import bf.cloud.android.base.BFYunApplication;


public class MainActivity extends Activity implements VideoPublisher.ProgressListener, VideoPublisher.CompleteListener, OnItemClickListener  {

	final String TAG = "MainActivity";
	
	final String tokenRequestUrl = "http://192.168.202.99:8080/api/token/upload";
	final String videoListUrl = "http://192.168.202.99:8080/api/video/all";
	Uri captureFileUri;

	Handler mainHandler;
	
	ArrayList<String> videoItems=new ArrayList<String>();
	ArrayAdapter<String> adapter;
	
	ArrayList<String> urls=new ArrayList<String>();

	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        
        showProgressBar(false);
        
		final ListView listView = (ListView) findViewById(R.id.videoListView);
       
        adapter=new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,
                videoItems);
        
        listView.setAdapter(adapter);
        
        listView.setOnItemClickListener((OnItemClickListener) this);

        
        VideoPublisher.getInstance().init();
		BFYunApplication.createInstance();
		BFYunApplication.getInstance().setApp(this.getApplication());


        mainHandler = new Handler(this.getMainLooper());
       
        
        final Button captureBtn = (Button) findViewById(R.id.captureBtn);
        captureBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	
            	
            	Intent intent = new Intent( android.provider.MediaStore.ACTION_VIDEO_CAPTURE );
            	
            	File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "BFCloudVideos");
            	
            	if (! mediaStorageDir.exists()) {
                    if (! mediaStorageDir.mkdirs()) {
                        Log.d(TAG, "failed to create directory");
                        return;
                    }
                }


            	File mediaFile = new File(mediaStorageDir.getPath() + File.separator + UUID.randomUUID().toString() + ".mp4");
            	
            	captureFileUri = Uri.fromFile(mediaFile);
            	
            	 intent.putExtra(MediaStore.EXTRA_OUTPUT, captureFileUri);
            	 intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
            	
            	startActivityForResult(intent, 1);            	
            	
            	}
        });
        
        final Button refreshBtn = (Button) findViewById(R.id.refreshBtn);
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	
            	refreshVideoList();
            	
            }});        
        
        refreshVideoList();
    }
    
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case 1:
                if (resultCode == RESULT_OK) {
                    
                    showProgressBar(true);

                    uploadFile(captureFileUri.getPath());

                    
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    
    
    protected void uploadFile(final String path){

        new Thread(new Runnable(){

			@Override
			public void run() {
				doUploadFile(path);				
			}}).start();
        
    }
    
    protected void doUploadFile(final String path){
       	File file = new File(path);
       	String fileName = file.getName(); 
       	long length = file.length();  	
      	
       	final String token = requestUploadToken(fileName, length);
       	
       	
       	mainHandler.post(new Runnable(){
			@Override
			public void run() {				
				afterGetToken(path, token);
			}});
       	
    }
    
    protected void afterGetToken(String path, String token){
       	if (token.length() == 0){
            Toast.makeText(this, "request token fail!", Toast.LENGTH_LONG).show();
            return;
       	}
       	
        
        int mTaskHandle = VideoPublisher.getInstance().createUploadTask(path, token);

        VideoPublisher.getInstance().registerProgressListener(mTaskHandle, this);
        VideoPublisher.getInstance().registerCompleteListener(mTaskHandle, this);

        VideoPublisher.getInstance().startTask(mTaskHandle);

        
    }
    
    
    protected String requestUploadToken(String name, long size){
    
    	   HttpClient httpclient = new DefaultHttpClient();

    	    List<NameValuePair> params = new ArrayList<NameValuePair>();
    	    params.add( new BasicNameValuePair( "name", name ) );
    	    params.add( new BasicNameValuePair( "size", ""+size ) );
    	    params.add( new BasicNameValuePair( "title", DeviceInfo.getDeviceName() ) );
    	    String url = tokenRequestUrl + "?" + URLEncodedUtils.format( params, "utf-8" );

    	    // Prepare a request object
    	    HttpGet httpget = new HttpGet(url);

    	    
    	    try{
    	    	HttpResponse resp = httpclient.execute(httpget);
    	    	
    	    	if (resp.getStatusLine().getStatusCode() != 200)
    	    		return "";
    	    	
    	    	return EntityUtils.toString(resp.getEntity());    	    	
    	    
    	    }catch(Exception e){
    	    	Log.e(TAG, e.toString());
    	    }
    	
    	return "";
    }
    
    
    @Override
    public void onProgress(int taskHandle, long pos, long max) {
        ProgressBar v = (ProgressBar) findViewById(R.id.progressBar);
        int percent = (int) (((double)pos / max) * 100);
        v.setProgress(percent);
    }

    @Override
    public void onComplete(int taskHandle, int errorCode, String errorMsg) {
        final String msg;
        if (errorCode == 0) {
            msg = "Upload successfully!";
        } else {
            msg = String.format("Upload failed. error code: %d.", errorCode);
        }

       	mainHandler.post(new Runnable(){
			@Override
			public void run() {				
				afterComplete(msg);
			}});
        
    }

	protected void afterComplete(String msg){

		Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
		
        showProgressBar(false);
		refreshVideoList();		
	}  

	
	
	protected void refreshVideoList(){

 	   final HttpClient httpclient = new DefaultHttpClient();
	   final HttpGet httpget = new HttpGet(videoListUrl);

	    
	    new Thread(new Runnable(){

			@Override
			public void run() {

			    try{
			    	final HttpResponse resp = httpclient.execute(httpget);

			    	
			       	mainHandler.post(new Runnable(){
						@Override
						public void run() {
							try {
								
								byte[] buf = new byte[(int) resp.getEntity().getContentLength()];
								resp.getEntity().getContent().read(buf);
								
								refreshCompleted(new String(buf, "UTF-8"));
								
							} catch (Exception e) {
							}
							

						}});	    	
			    	    	    	
			    
			    }catch(Exception e){
			    	Log.e(TAG, e.toString());
			    }
				
			}
	    	
	    }).start();
	    
	}
	
	protected void refreshCompleted(String json){
        
		final ListView listView = (ListView) findViewById(R.id.videoListView);

		
		videoItems.clear();
		urls.clear();
		
		try {
			
			JSONArray items = new JSONArray(json);
			
			for (int i=0; i < items.length(); i++)
			{
			    try {
			        JSONObject item = items.getJSONObject(i);

			        String title = item.getString("Title");
			        String url = item.getString("Url");
			        
			        String xTitle = title.length() > 0 ? title : "Untitled";
			        if (url.length() == 0)
			        	xTitle = xTitle + "(Processing)";
			        				        
			        
			        videoItems.add(xTitle);
			        
			        
			        
			        urls.add(url);
		        
			    } catch (JSONException e) {
			        // Oops
			    }
			}
			
			
			
		} catch (JSONException e) {
			e.printStackTrace();
		}

        adapter.notifyDataSetChanged();
		
	}
	
	  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

		 String url = urls.get(position);
		 String name = videoItems.get(position);
		  
        VodPlayer.play(this, url , "", name, 0, true, null);
			    
	 }

	  protected void showProgressBar(boolean show){
	  
		  ProgressBar v = (ProgressBar) findViewById(R.id.progressBar);
		  v.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
      }

	
}

