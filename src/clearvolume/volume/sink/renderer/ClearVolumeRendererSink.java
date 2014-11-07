package clearvolume.volume.sink.renderer;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import clearvolume.renderer.ClearVolumeRendererInterface;
import clearvolume.transferf.TransferFunction;
import clearvolume.transferf.TransferFunctions;
import clearvolume.volume.Volume;
import clearvolume.volume.VolumeManager;
import clearvolume.volume.sink.NullVolumeSink;
import clearvolume.volume.sink.relay.RelaySinkAdapter;
import clearvolume.volume.sink.relay.RelaySinkInterface;

public class ClearVolumeRendererSink extends RelaySinkAdapter	implements
																															RelaySinkInterface
{

	private ClearVolumeRendererInterface mClearVolumeRendererInterface;
	private VolumeManager mVolumeManager;
	private long mWaitForCopyTimeout;
	private TimeUnit mTimeUnit;

	private volatile long mLastTimePointDisplayed = Long.MIN_VALUE;

	public ClearVolumeRendererSink(	ClearVolumeRendererInterface pClearVolumeRendererInterface,
																	VolumeManager pVolumeManager,
																	long pWaitForCopyTimeout,
																	TimeUnit pTimeUnit)
	{
		super();
		mClearVolumeRendererInterface = pClearVolumeRendererInterface;
		mVolumeManager = pVolumeManager;
		mWaitForCopyTimeout = pWaitForCopyTimeout;
		mTimeUnit = pTimeUnit;

	}

	@Override
	public void sendVolume(Volume<?> pVolume)
	{

		final ByteBuffer lVolumeDataBuffer = pVolume.getDataBuffer();
		final long lVoxelWidth = pVolume.getWidthInVoxels();
		final long lVoxelHeight = pVolume.getHeightInVoxels();
		final long lVoxelDepth = pVolume.getDepthInVoxels();

		final double lRealWidth = pVolume.getWidthInRealUnits();
		final double lRealHeight = pVolume.getHeightInRealUnits();
		final double lRealDepth = pVolume.getDepthInRealUnits();

		final long lTimePointIndex = pVolume.getTimeIndex();
		final int lChannelID = pVolume.getChannelID();
		final int lNumberOfRenderLayers = mClearVolumeRendererInterface.getNumberOfRenderLayers();
		final int lRenderLayer = lChannelID % lNumberOfRenderLayers;

		mClearVolumeRendererInterface.setCurrentRenderLayer(lRenderLayer);

		TransferFunction lTransferFunction;
		final float[] lColor = pVolume.getColor();
		if (lColor != null)
			lTransferFunction = TransferFunctions.getGradientForColor(lColor);
		else
			lTransferFunction = TransferFunctions.getGradientForColor(lRenderLayer);

		mClearVolumeRendererInterface.setTransfertFunction(lTransferFunction);
		mClearVolumeRendererInterface.setVolumeDataBuffer(lVolumeDataBuffer,
																											lVoxelWidth,
																											lVoxelHeight,
																											lVoxelDepth,
																											lRealWidth,
																											lRealHeight,
																											lRealDepth);

		// if (lTimePointIndex > mLastTimePointDisplayed)
		{
			mClearVolumeRendererInterface.requestDisplay();

			mClearVolumeRendererInterface.waitToFinishDataBufferCopy(	mWaitForCopyTimeout,
																																mTimeUnit);
			mLastTimePointDisplayed = lTimePointIndex;
		}

		if (getRelaySink() != null)
			getRelaySink().sendVolume(pVolume);
		else
			pVolume.makeAvailableToManager();/**/

	}

	@Override
	public VolumeManager getManager()
	{
		if (!(getRelaySink() instanceof NullVolumeSink))
			if (getRelaySink() != null)
				return getRelaySink().getManager();
		return mVolumeManager;
	}

}