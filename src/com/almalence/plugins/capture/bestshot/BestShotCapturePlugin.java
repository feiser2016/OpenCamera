/*
The contents of this file are subject to the Mozilla Public License
Version 1.1 (the "License"); you may not use this file except in
compliance with the License. You may obtain a copy of the License at
http://www.mozilla.org/MPL/

Software distributed under the License is distributed on an "AS IS"
basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
License for the specific language governing rights and limitations
under the License.

The Original Code is collection of files collectively known as Open Camera.

The Initial Developer of the Original Code is Almalence Inc.
Portions created by Initial Developer are Copyright (C) 2013 
by Almalence Inc. All Rights Reserved.
 */

package com.almalence.plugins.capture.bestshot;

import java.nio.ByteBuffer;
import java.util.Arrays;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.util.Log;

/* <!-- +++
 import com.almalence.opencam_plus.CameraController;
 import com.almalence.opencam_plus.MainScreen;
 import com.almalence.opencam_plus.PluginCapture;
 import com.almalence.opencam_plus.PluginManager;
 import com.almalence.opencam_plus.R;
 +++ --> */
// <!-- -+-
import com.almalence.opencam.cameracontroller.CameraController;
import com.almalence.opencam.MainScreen;
import com.almalence.opencam.PluginCapture;
import com.almalence.opencam.PluginManager;
import com.almalence.opencam.R;
//-+- -->

import com.almalence.SwapHeap;
import com.almalence.YuvImage;

/***
 * Implements burst capture plugin - captures predefined number of images
 ***/

public class BestShotCapturePlugin extends PluginCapture
{
	// defaul val. value should come from config
	private int				imageAmount	= 5;

	private int				imagesTaken	= 0;

	private static String	sImagesAmountPref;

	public BestShotCapturePlugin()
	{
		super("com.almalence.plugins.bestshotcapture", R.xml.preferences_capture_bestshot, 0,
				R.drawable.gui_almalence_mode_bestshot, "Best Shot images");
	}

	@Override
	public void onCreate()
	{
		sImagesAmountPref = MainScreen.getInstance().getResources().getString(R.string.Preference_BestShotImagesAmount);
	}

	@Override
	public void onResume()
	{
		takingAlready = false;
		imagesTaken = 0;
		inCapture = false;
		refreshPreferences();

		MainScreen.setCaptureYUVFrames(true);
	}

	private void refreshPreferences()
	{
		try
		{
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainScreen.getMainContext());
			imageAmount = Integer.parseInt(prefs.getString(sImagesAmountPref, "5"));
		} catch (Exception e)
		{
			Log.v("Bestshot capture", "Cought exception " + e.getMessage());
		}

		switch (imageAmount)
		{
		case 3:
			quickControlIconID = R.drawable.gui_almalence_mode_burst3;
			break;
		case 5:
			quickControlIconID = R.drawable.gui_almalence_mode_burst5;
			break;
		case 10:
			quickControlIconID = R.drawable.gui_almalence_mode_burst10;
			break;
		default:
			break;
		}
	}

	@Override
	public void onQuickControlClick()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainScreen.getMainContext());
		int val = Integer.parseInt(prefs.getString(sImagesAmountPref, "5"));
		int selected = 0;
		switch (val)
		{
		case 3:
			selected = 0;
			break;
		case 5:
			selected = 1;
			break;
		case 10:
			selected = 2;
			break;
		default:
			break;
		}
		selected = (selected + 1) % 3;

		Editor editor = prefs.edit();
		switch (selected)
		{
		case 0:
			quickControlIconID = R.drawable.gui_almalence_mode_burst3;
			editor.putString("BestshotImagesAmount", "3");
			break;
		case 1:
			quickControlIconID = R.drawable.gui_almalence_mode_burst5;
			editor.putString("BestshotImagesAmount", "5");
			break;
		case 2:
			quickControlIconID = R.drawable.gui_almalence_mode_burst10;
			editor.putString("BestshotImagesAmount", "10");
			break;
		default:
			break;
		}
		editor.commit();
	}

	@Override
	public void onGUICreate()
	{
		MainScreen.getGUIManager().showHelp(MainScreen.getInstance().getString(R.string.Bestshot_Help_Header),
				MainScreen.getInstance().getResources().getString(R.string.Bestshot_Help),
				R.drawable.plugin_help_bestshot, "bestShotShowHelp");
	}

	public boolean delayedCaptureSupported()
	{
		return true;
	}

	public void takePicture()
	{
		if (!inCapture)
		{
			inCapture = true;
			refreshPreferences();
			takingAlready = true;
			
			// play tick sound
			MainScreen.getGUIManager().showCaptureIndication();
			MainScreen.getInstance().playShutter();

			try
			{
				int[] pause = new int[imageAmount];
				Arrays.fill(pause, 50);
				requestID = CameraController.captureImagesWithParams(imageAmount, CameraController.YUV, pause, null);
			} catch (Exception e)
			{
				e.printStackTrace();
				Log.e("Bestshot takePicture() failed", "takePicture: " + e.getMessage());
				inCapture = false;
				takingAlready = false;
				PluginManager.getInstance().sendMessage(PluginManager.MSG_BROADCAST, 
						PluginManager.MSG_CONTROL_UNLOCKED);
				MainScreen.getGUIManager().lockControls = false;
			}
		}
	}

	
	@Override
	public void onImageTaken(int frame, byte[] frameData, int frame_len, boolean isYUV)
	{
		imagesTaken++;

		if (frame == 0)
		{
			Log.e("Bestshot", "Load to heap failed");
			PluginManager.getInstance().sendMessage(PluginManager.MSG_CAPTURE_FINISHED, 
					String.valueOf(SessionID));

			imagesTaken = 0;
			MainScreen.getInstance().muteShutter(false);
			inCapture = false;
			return;
		}
		String frameName = "frame" + imagesTaken;
		String frameLengthName = "framelen" + imagesTaken;

		PluginManager.getInstance().addToSharedMem(frameName + SessionID, String.valueOf(frame));
		PluginManager.getInstance().addToSharedMem(frameLengthName + SessionID, String.valueOf(frame_len));
		PluginManager.getInstance().addToSharedMem("frameorientation" + imagesTaken + SessionID,
				String.valueOf(MainScreen.getGUIManager().getDisplayOrientation()));
		PluginManager.getInstance().addToSharedMem("framemirrored" + imagesTaken + SessionID,
				String.valueOf(CameraController.isFrontCamera()));
		PluginManager.getInstance().addToSharedMem("isyuv" + SessionID, String.valueOf(isYUV));

		if (imagesTaken == 1 && !isYUV)
			PluginManager.getInstance().addToSharedMemExifTagsFromJPEG(frameData, SessionID, -1);

		try
		{
			CameraController.startCameraPreview();
		} catch (RuntimeException e)
		{
			Log.e("Bestshot", "StartPreview fail");
			PluginManager.getInstance().sendMessage(PluginManager.MSG_CAPTURE_FINISHED, 
					String.valueOf(SessionID));

			imagesTaken = 0;
			MainScreen.getInstance().muteShutter(false);
			inCapture = false;
			return;
		}
		
		if (imagesTaken >= imageAmount)
		{
			PluginManager.getInstance().addToSharedMem("amountofcapturedframes" + SessionID,
					String.valueOf(imagesTaken));

			PluginManager.getInstance().sendMessage(PluginManager.MSG_CAPTURE_FINISHED, 
					String.valueOf(SessionID));
			
			Log.e("Bestshot", "CAPTURE FINISHED");
			
			imagesTaken = 0;
			inCapture = false;
		}
		inCapture = false;
		takingAlready = false;	
	}

	@TargetApi(21)
	@Override
	public void onCaptureCompleted(CaptureResult result)
	{
		if (result.getSequenceId() == requestID)
		{
			if (imagesTaken == 1)
				PluginManager.getInstance().addToSharedMemExifTagsFromCaptureResult(result, SessionID);
		}
	}

	@Override
	public void onAutoFocus(boolean paramBoolean)
	{
		if (takingAlready)
			takePicture();
	}

	
	@Override
	public void onPreviewFrame(byte[] data)
	{
	}
}
