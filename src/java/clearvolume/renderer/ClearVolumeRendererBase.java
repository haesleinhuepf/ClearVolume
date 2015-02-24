package clearvolume.renderer;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sqrt;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;

import javax.swing.SwingUtilities;

import clearvolume.ClearVolumeCloseable;
import clearvolume.controller.RotationControllerInterface;
import clearvolume.projections.ProjectionAlgorithm;
import clearvolume.renderer.processors.Processor;
import clearvolume.transferf.TransferFunction;
import clearvolume.transferf.TransferFunctions;
import clearvolume.volume.Volume;
import clearvolume.volume.VolumeManager;

/**
 * Class ClearVolumeRendererBase
 *
 * Instances of this class ...
 *
 * @author Loic Royer (2014), Florian Jug (2015)
 *
 */
public abstract class ClearVolumeRendererBase	implements
																							ClearVolumeRendererInterface,
																							ClearVolumeCloseable
{

	/**
	 * Number of render layers.
	 */
	private int mNumberOfRenderLayers;
	private volatile int mCurrentRenderLayerIndex = 0;

	/**
	 * Number of bytes per voxel used by this renderer
	 */
	private volatile int mBytesPerVoxel = 1;

	/**
	 * Rotation controller in addition to the mouse
	 */
	private RotationControllerInterface mRotationController;

	/**
	 * Projection algorithm used
	 */
	private ProjectionAlgorithm mProjectionAlgorithm = ProjectionAlgorithm.MaxProjection;

	/**
	 * Transfer functions used
	 */
	private final TransferFunction[] mTransferFunctions;

	private volatile boolean[] mLayerVisiblityFlagArray;

	// geometric, brigthness an contrast settings.
	private volatile float mTranslationX = 0;
	private volatile float mTranslationY = 0;
	private volatile float mTranslationZ = 0;
	private volatile float mRotationX = 0;
	private volatile float mRotationY = 0;
	private volatile float mScaleX = 1.0f;
	private volatile float mScaleY = 1.0f;
	private volatile float mScaleZ = 1.0f;

	// private volatile float mDensity;
	private volatile float[] mBrightness;
	private volatile float[] mTransferFunctionRangeMin;
	private volatile float[] mTransferFunctionRangeMax;
	private volatile float[] mGamma;
	private volatile float[] mQuality;
	private volatile float[] mDithering;

	private volatile boolean mVolumeRenderingParametersChanged = true;

	// volume dimensions settings
	private volatile long mVolumeSizeX;
	private volatile long mVolumeSizeY;
	private volatile long mVolumeSizeZ;
	private volatile double mVoxelSizeX;

	private volatile double mVoxelSizeY;
	private volatile double mVoxelSizeZ;

	private volatile boolean mVolumeDimensionsChanged;

	// data copy locking and waiting
	private final Object[] mSetVolumeDataBufferLocks;
	private volatile ByteBuffer[] mVolumeDataByteBuffers;
	private final AtomicIntegerArray mDataBufferCopyIsFinished;

	// Control frame:
	private ControlPanelJFrame mControlFrame;

	// Map of processors:
	protected Map<String, Processor<?>> mProcessorsMap = new ConcurrentHashMap<>();

	// List of Capture Listeners
	protected ArrayList<VolumeCaptureListener> mVolumeCaptureListenerList = new ArrayList<VolumeCaptureListener>();
	protected volatile boolean mVolumeCaptureFlag = false;

	// Adaptive LOD controller:
	protected AdaptiveLODController mAdaptiveLODController;

	public ClearVolumeRendererBase(final int pNumberOfRenderLayers)
	{
		super();

		mNumberOfRenderLayers = pNumberOfRenderLayers;
		mSetVolumeDataBufferLocks = new Object[pNumberOfRenderLayers];
		mVolumeDataByteBuffers = new ByteBuffer[pNumberOfRenderLayers];
		mDataBufferCopyIsFinished = new AtomicIntegerArray(pNumberOfRenderLayers);
		mTransferFunctions = new TransferFunction[pNumberOfRenderLayers];
		mLayerVisiblityFlagArray = new boolean[pNumberOfRenderLayers];
		mBrightness = new float[pNumberOfRenderLayers];
		mTransferFunctionRangeMin = new float[pNumberOfRenderLayers];
		mTransferFunctionRangeMax = new float[pNumberOfRenderLayers];
		mGamma = new float[pNumberOfRenderLayers];
		mQuality = new float[pNumberOfRenderLayers];
		mDithering = new float[pNumberOfRenderLayers];

		for (int i = 0; i < pNumberOfRenderLayers; i++)
		{
			mSetVolumeDataBufferLocks[i] = new Object();
			mDataBufferCopyIsFinished.set(i, 0);
			mTransferFunctions[i] = TransferFunctions.getGradientForColor(i);
			mLayerVisiblityFlagArray[i] = true;
			mBrightness[i] = 1;
			mTransferFunctionRangeMin[i] = 0f;
			mTransferFunctionRangeMax[i] = 1f;
			mGamma[i] = 1.0f;
			mQuality[i] = 0.75f;
			mDithering[i] = 1f;
		}

		mAdaptiveLODController = new AdaptiveLODController();

	}

	/**
	 * Sets the number of bytes per voxel for this renderer. This is _usually_ set
	 * at construction time and should not be modified later
	 *
	 * @param pBytesPerVoxel
	 *          bytes-per-voxel
	 */
	public void setBytesPerVoxel(final int pBytesPerVoxel)
	{
		mBytesPerVoxel = pBytesPerVoxel;
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#getBytesPerVoxel()
	 */
	@Override
	public int getBytesPerVoxel()
	{
		return mBytesPerVoxel;
	}

	/**
	 * Returns the state of the flag indicating whether current/new rendering
	 * parameters have been used for last rendering.
	 *
	 * @return true if rednering parameters up-to-date.
	 */
	public boolean haveVolumeRenderingParametersChanged()
	{
		return mVolumeRenderingParametersChanged;
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#notifyChangeOfVolumeRenderingParameters()
	 */
	@Override
	public void notifyChangeOfVolumeRenderingParameters()
	{
		mVolumeRenderingParametersChanged = true;
		getAdaptiveLODController().notifyUserInteractionInProgress();
	}

	/**
	 * Clears the state of the update-volume-parameters flag
	 */
	public void clearChangeOfVolumeParametersFlag()
	{
		mVolumeRenderingParametersChanged = false;
	}

	/**
	 * Sets the volume size in 'real' units of the volume (um, cm, ...) The apsect
	 * ratio for the volume is set accordingly.
	 *
	 * @param pVolumeSizeX
	 * @param pVolumeSizeY
	 * @param pVolumeSizeZ
	 */
	public void setVolumeSize(final double pVolumeSizeX,
														final double pVolumeSizeY,
														final double pVolumeSizeZ)
	{
		final double lMaxXYZ = Math.max(Math.max(	pVolumeSizeX,
																							pVolumeSizeY),
																		pVolumeSizeZ);

		setScaleX(pVolumeSizeX / lMaxXYZ);
		setScaleY(pVolumeSizeY / lMaxXYZ);
		setScaleZ(pVolumeSizeZ / lMaxXYZ);
	}

	/**
	 * Returns the volume size along x axis.
	 *
	 * @return volume size along x
	 */
	public long getVolumeSizeX()
	{
		return mVolumeSizeX;
	}

	/**
	 * Returns the volume size along y axis.
	 *
	 * @return volume size along y
	 */
	public long getVolumeSizeY()
	{
		return mVolumeSizeY;
	}

	/**
	 * Returns the volume size along z axis.
	 *
	 * @return volume size along z
	 */
	public long getVolumeSizeZ()
	{
		return mVolumeSizeZ;
	}

	public double getVoxelSizeX()
	{
		return mVoxelSizeX;
	}

	public double getVoxelSizeY()
	{
		return mVoxelSizeY;
	}

	public double getVoxelSizeZ()
	{
		return mVoxelSizeZ;
	}

	/**
	 * Returns whether the volume dimensions have been changed since last data
	 * upload.
	 *
	 * @return true if volume dimensions changed.
	 */
	public boolean haveVolumeDimensionsChanged()
	{
		return mVolumeDimensionsChanged;
	}

	/**
	 *
	 */
	public void clearVolumeDimensionsChanged()
	{
		mVolumeDimensionsChanged = false;
	}

	/**
	 * Sets the scale factor for the volume along the x axis.
	 *
	 * @param pScaleX
	 *          scale factor along x
	 */
	public void setScaleX(final double pScaleX)
	{
		mScaleX = (float) pScaleX;
		notifyChangeOfVolumeRenderingParameters();
	}

	/**
	 * Sets the scale factor for the volume along the y axis.
	 *
	 * @param pScaleY
	 *          scale factor along y
	 */
	public void setScaleY(final double pScaleY)
	{
		mScaleY = (float) pScaleY;
		notifyChangeOfVolumeRenderingParameters();
	}

	/**
	 * Sets the scale factor for the volume along the z axis.
	 *
	 * @param pScaleZ
	 *          scale factor along z
	 */
	public void setScaleZ(final double pScaleZ)
	{
		mScaleZ = (float) pScaleZ;
		notifyChangeOfVolumeRenderingParameters();
	}

	/**
	 * Gets active flag for the current render layer.
	 *
	 * @return
	 */
	@Override
	public boolean isLayerVisible()
	{
		return isLayerVisible(getCurrentRenderLayerIndex());
	}

	/**
	 * Gets active flag for the given render layer.
	 *
	 * @return
	 */
	@Override
	public boolean isLayerVisible(final int pRenderLayerIndex)
	{
		return mLayerVisiblityFlagArray[pRenderLayerIndex];
	}

	/**
	 * Sets active flag for the current render layer.
	 *
	 * @param pVisble
	 */
	@Override
	public void setLayerVisible(boolean pVisble)
	{
		setLayerVisible(getCurrentRenderLayerIndex(), pVisble);
	}

	/**
	 * Sets active flag for the given render layer.
	 *
	 * @param pRenderLayerIndex
	 * @param pVisble
	 */
	@Override
	public void setLayerVisible(final int pRenderLayerIndex,
															final boolean pVisble)
	{
		mLayerVisiblityFlagArray[pRenderLayerIndex] = pVisble;
		notifyChangeOfVolumeRenderingParameters();
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#resetBrightnessAndGammaAndTransferFunctionRanges()
	 */
	@Override
	public void resetBrightnessAndGammaAndTransferFunctionRanges()
	{
		for (int i = 0; i < getNumberOfRenderLayers(); i++)
		{
			mBrightness[i] = 1.0f;
			mGamma[i] = 1.0f;
			mTransferFunctionRangeMin[i] = 0.0f;
			mTransferFunctionRangeMax[i] = 1.0f;
		}
		notifyChangeOfVolumeRenderingParameters();
	}

	/**
	 * Adds to the brightness of the image
	 *
	 * @param pBrightnessDelta
	 */
	@Override
	public void addBrightness(final double pBrightnessDelta)
	{
		addBrightness(getCurrentRenderLayerIndex(), pBrightnessDelta);

	}

	/**
	 * Adds to the brightness of the image for a given render layer index
	 *
	 * @param pRenderLayer
	 * @param pBrightnessDelta
	 */
	@Override
	public void addBrightness(final int pRenderLayerIndex,
														final double pBrightnessDelta)
	{
		setBrightness(pRenderLayerIndex,
									getBrightness() + pBrightnessDelta);
	}

	/**
	 * 
	 * Returns the brightness level of the current render layer.
	 *
	 * @return brightness level.
	 */
	@Override
	public double getBrightness()
	{
		return getBrightness(getCurrentRenderLayerIndex());
	}

	/**
	 * Returns the brightness level of a given render layer index.
	 *
	 * @param pRenderLayerIndex
	 * @return brightness level.
	 */
	@Override
	public double getBrightness(final int pRenderLayerIndex)
	{
		return mBrightness[pRenderLayerIndex];
	}

	/**
	 * Sets brightness.
	 *
	 * @param pBrightness
	 *          brightness level
	 */
	@Override
	public void setBrightness(final double pBrightness)
	{
		setBrightness(getCurrentRenderLayerIndex(), pBrightness);
	}

	/**
	 * Sets brightness for a given render layer index.
	 *
	 * @param pRenderLayerIndex
	 * @param pBrightness
	 *          brightness level
	 */
	@Override
	public void setBrightness(final int pRenderLayerIndex,
														final double pBrightness)
	{
		mBrightness[pRenderLayerIndex] = (float) clamp(	pBrightness,
																										0,
																										getBytesPerVoxel() == 1	? 16
																																						: 256);

		notifyChangeOfVolumeRenderingParameters();
	}

	/**
	 * Returns the Gamma value.
	 *
	 * @return gamma value
	 */
	@Override
	public double getGamma()
	{
		return getGamma(getCurrentRenderLayerIndex());
	}

	/**
	 * Returns the Gamma value.
	 *
	 * @param pRenderLayerIndex
	 * @return
	 */
	@Override
	public double getGamma(final int pRenderLayerIndex)
	{
		return mGamma[pRenderLayerIndex];
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#setGamma(double)
	 */
	@Override
	public void setGamma(final double pGamma)
	{
		setGamma(getCurrentRenderLayerIndex(), pGamma);
	}

	/**
	 * Sets the gamma for a given render layer index.
	 *
	 * @param pRenderLayerIndex
	 * @param pGamma
	 */
	@Override
	public void setGamma(	final int pRenderLayerIndex,
												final double pGamma)

	{
		mGamma[pRenderLayerIndex] = (float) pGamma;
		notifyChangeOfVolumeRenderingParameters();
	}

	/**
	 * @param pRenderLayerIndex
	 * @param pDithering
	 *          new dithering level for render layer
	 */
	@Override
	public void setDithering(int pRenderLayerIndex, double pDithering)
	{
		mDithering[pRenderLayerIndex] = (float) pDithering;
		notifyChangeOfVolumeRenderingParameters();
	};

	/**
	 * Returns samount of dithering [0,1] for a given render layer.
	 * 
	 * @param pRenderLayerIndex
	 * @return dithering
	 */
	@Override
	public float getDithering(int pRenderLayerIndex)
	{
		return mDithering[pRenderLayerIndex];
	};

	/**
	 * Sets the amount of dithering [0,1] for a given render layer.
	 * 
	 * @param pRenderLayerIndex
	 * 
	 * @param pRenderLayerIndex
	 * @param pQuality
	 *          new quality level for render layer
	 */
	@Override
	public void setQuality(int pRenderLayerIndex, double pQuality)
	{
		pQuality = max(min(pQuality, 1), 0);
		mQuality[pRenderLayerIndex] = (float) pQuality;
		notifyChangeOfVolumeRenderingParameters();
	};

	/**
	 * Returns the quality level [0,1] for a given render layer.
	 * 
	 * @param pRenderLayerIndex
	 * @return quality level
	 */
	@Override
	public float getQuality(int pRenderLayerIndex)
	{
		return mQuality[pRenderLayerIndex];
	};

	/**
	 * Returns the maximal number of steps during ray casting forna given layer.
	 * This value depends on the volume dimension and quality.
	 * 
	 * @param pRenderLayerIndex
	 * @return maximal number of steps
	 */
	public int getMaxSteps(final int pRenderLayerIndex)
	{
		return (int) (sqrt(getVolumeSizeX() * getVolumeSizeX()
												+ getVolumeSizeY()
												* getVolumeSizeY()
												+ getVolumeSizeZ()
												* getVolumeSizeZ()) * getQuality(pRenderLayerIndex));
	}

	/**
	 * Returns the minimum of the transfer function range for the current render
	 * layer.
	 *
	 * @return minimum
	 */
	@Override
	public double getTransferRangeMin()
	{
		return getTransferRangeMin(getCurrentRenderLayerIndex());
	}

	/**
	 * Returns the minimum of the transfer function range for a given render
	 * layer.
	 *
	 * @param pRenderLayerIndex
	 * @return
	 */
	@Override
	public double getTransferRangeMin(final int pRenderLayerIndex)
	{
		return mTransferFunctionRangeMin[pRenderLayerIndex];
	}

	/**
	 * 
	 * Returns the maximum of the transfer function range for the current render
	 * layer index.
	 *
	 * @return minimum
	 */
	@Override
	public double getTransferRangeMax()
	{
		return getTransferRangeMax(getCurrentRenderLayerIndex());
	}

	/**
	 * Returns the maximum of the transfer function range for a given render layer
	 * index.
	 *
	 * @param pRenderLayerIndex
	 * @return
	 */
	@Override
	public double getTransferRangeMax(final int pRenderLayerIndex)
	{
		return mTransferFunctionRangeMax[pRenderLayerIndex];
	}

	/**
	 * Sets the transfer function range min and max for the current render layer
	 * index.
	 *
	 * @param pTransferRangeMin
	 * @param pTransferRangeMax
	 */
	@Override
	public void setTransferFunctionRange(	final double pTransferRangeMin,
																				final double pTransferRangeMax)
	{
		setTransferFunctionRange(	getCurrentRenderLayerIndex(),
															pTransferRangeMin,
															pTransferRangeMax);
	}

	/**
	 * Sets the transfer function range min and max for a given render layer
	 * index.
	 *
	 * @param pRenderLayerIndex
	 * @param pTransferRangeMin
	 * @param pTransferRangeMax
	 */
	@Override
	public void setTransferFunctionRange(	final int pRenderLayerIndex,
																				final double pTransferRangeMin,
																				final double pTransferRangeMax)
	{
		mTransferFunctionRangeMin[pRenderLayerIndex] = (float) clamp(	pTransferRangeMin,
																																	0,
																																	1);
		mTransferFunctionRangeMax[pRenderLayerIndex] = (float) clamp(	pTransferRangeMax,
																																	0,
																																	1);
		notifyChangeOfVolumeRenderingParameters();
	}

	/**
	 * Sets transfer range minimum, must be within [0,1].
	 *
	 * @param pTransferRangeMin
	 *          minimum
	 */
	@Override
	public void setTransferFunctionRangeMin(final double pTransferRangeMin)
	{
		setTransferFunctionRangeMin(getCurrentRenderLayerIndex(),
																pTransferRangeMin);
	}

	/**
	 * Sets transfer range minimum, must be within [0,1].
	 *
	 * @param pRenderLayerIndex
	 * @param pTransferRangeMin
	 */
	@Override
	public void setTransferFunctionRangeMin(final int pRenderLayerIndex,
																					final double pTransferRangeMin)
	{
		mTransferFunctionRangeMin[pRenderLayerIndex] = (float) clamp(	pTransferRangeMin,
																																	0,
																																	1);

		notifyChangeOfVolumeRenderingParameters();
	}

	/**
	 * Sets transfer function range maximum, must be within [0,1].
	 *
	 * @param pTransferRangeMax
	 *          maximum
	 */
	@Override
	public void setTransferFunctionRangeMax(final double pTransferRangeMax)
	{
		setTransferFunctionRangeMax(getCurrentRenderLayerIndex(),
																pTransferRangeMax);
	}

	/**
	 * Sets transfer function range maximum, must be within [0,1].
	 *
	 * @param pRenderLayerIndex
	 * @param pTransferRangeMax
	 */
	@Override
	public void setTransferFunctionRangeMax(final int pRenderLayerIndex,
																					final double pTransferRangeMax)
	{
		mTransferFunctionRangeMax[pRenderLayerIndex] = (float) clamp(	pTransferRangeMax,
																																	0,
																																	1);
		notifyChangeOfVolumeRenderingParameters();
	}

	/**
	 * Translates the minimum of the transfer function range.
	 *
	 * @param pDelta
	 *          translation amount
	 */
	@Override
	public void addTransferFunctionRangeMin(final double pDelta)
	{
		setTransferFunctionRangeMin(getCurrentRenderLayerIndex(), pDelta);
	}

	/**
	 * Translates the minimum of the transfer function range.
	 *
	 * @param pRenderLayerIndex
	 * @param pDelta
	 */
	@Override
	public void addTransferFunctionRangeMin(final int pRenderLayerIndex,
																					final double pDelta)
	{
		setTransferFunctionRangeMin(getTransferRangeMin(pRenderLayerIndex) + pDelta);
	}

	/**
	 * Translates the maximum of the transfer function range.
	 *
	 * @param pDelta
	 *          translation amount
	 */
	@Override
	public void addTransferFunctionRangeMax(final double pDelta)
	{
		addTransferFunctionRangeMax(getCurrentRenderLayerIndex(), pDelta);
	}

	/**
	 * Translates the maximum of the transfer function range.
	 *
	 * @param pRenderLayerIndex
	 * @param pDelta
	 */
	@Override
	public void addTransferFunctionRangeMax(final int pRenderLayerIndex,
																					final double pDelta)
	{
		setTransferFunctionRangeMax(pRenderLayerIndex,
																getTransferRangeMax(pRenderLayerIndex) + pDelta);
	}

	/**
	 * Translates the transfer function range by a given amount.
	 *
	 * @param pTransferRangePositionDelta
	 *          amount of translation added
	 */
	@Override
	public void addTransferFunctionRangePosition(final double pTransferRangePositionDelta)
	{
		addTransferFunctionRangeMin(pTransferRangePositionDelta);
		addTransferFunctionRangeMax(pTransferRangePositionDelta);
	}

	/**
	 * Adds a certain amount (possibly negative) to the width of the transfer
	 * function range.
	 *
	 * @param pTransferRangeWidthDelta
	 *          value added to the width
	 */
	@Override
	public void addTransferFunctionRangeWidth(final double pTransferRangeWidthDelta)
	{
		addTransferFunctionRangeMin(-pTransferRangeWidthDelta);
		addTransferFunctionRangeMax(pTransferRangeWidthDelta);
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#addTranslationX(double)
	 */
	@Override
	public void addTranslationX(final double pDX)
	{
		mTranslationX += pDX;
		notifyChangeOfVolumeRenderingParameters();
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#addTranslationY(double)
	 */
	@Override
	public void addTranslationY(final double pDY)
	{
		mTranslationY += pDY;
		notifyChangeOfVolumeRenderingParameters();
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#addTranslationZ(double)
	 */
	@Override
	public void addTranslationZ(final double pDZ)
	{
		mTranslationZ += pDZ;
		notifyChangeOfVolumeRenderingParameters();
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#addRotationX(int)
	 */
	@Override
	public void addRotationX(final int pDRX)
	{
		mRotationX += pDRX;
		notifyChangeOfVolumeRenderingParameters();
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#addRotationY(int)
	 */
	@Override
	public void addRotationY(final int pDRY)
	{
		mRotationY += pDRY;
		notifyChangeOfVolumeRenderingParameters();
	}

	/**
	 * Returns volume scale along x.
	 *
	 * @return scale along x
	 */
	public double getScaleX()
	{
		return mScaleX;
	}

	/**
	 * Returns volume scale along y.
	 *
	 * @return scale along y
	 */
	public double getScaleY()
	{
		return mScaleY;
	}

	/**
	 * Returns volume scale along z.
	 *
	 * @return scale along z
	 */
	public double getScaleZ()
	{
		return mScaleZ;
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#getTranslationX()
	 */
	@Override
	public float getTranslationX()
	{
		return mTranslationX;
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#getTranslationY()
	 */
	@Override
	public float getTranslationY()
	{
		return mTranslationY;
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#getTranslationZ()
	 */
	@Override
	public float getTranslationZ()
	{
		return mTranslationZ;
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#getRotationY()
	 */
	@Override
	public float getRotationY()
	{
		return mRotationX;
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#getRotationX()
	 */
	@Override
	public float getRotationX()
	{
		return mRotationY;
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#setTransferFunction(clearvolume.transferf.TransferFunction)
	 */
	@Override
	public void setTransferFunction(final TransferFunction pTransfertFunction)
	{
		setTransferFunction(getCurrentRenderLayerIndex(),
												pTransfertFunction);
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#setTransferFunction(int,
	 *      clearvolume.transferf.TransferFunction)
	 */
	@Override
	public void setTransferFunction(final int pRenderLayerIndex,
																	final TransferFunction pTransfertFunction)
	{
		mTransferFunctions[pRenderLayerIndex] = pTransfertFunction;
	}

	/**
	 * Interface method implementation
	 *
	 * @return
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#getTransferFunction(int)
	 */
	@Override
	public TransferFunction getTransferFunction(final int pRenderLayerIndex)
	{
		return mTransferFunctions[pRenderLayerIndex];
	}

	/**
	 * Returns currently used transfer function.
	 *
	 * @return currently used transfer function
	 */
	@Override
	public TransferFunction getTransferFunction()
	{
		return mTransferFunctions[getCurrentRenderLayerIndex()];
	}

	/**
	 * Returns currently used mProjectionMatrix algorithm.
	 *
	 * @return currently used mProjectionMatrix algorithm
	 */
	public ProjectionAlgorithm getProjectionAlgorithm()
	{
		return mProjectionAlgorithm;
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#setProjectionAlgorithm(clearvolume.projections.ProjectionAlgorithm)
	 */
	@Override
	public void setProjectionAlgorithm(final ProjectionAlgorithm pProjectionAlgorithm)
	{
		mProjectionAlgorithm = pProjectionAlgorithm;
	}

	/**
	 * Returns current volume data buffer.
	 *
	 * @return current data buffer.
	 */
	@Deprecated
	public ByteBuffer getVolumeDataBuffer()
	{
		return getVolumeDataBuffer(getCurrentRenderLayerIndex());
	}

	/**
	 * Returns for a given index the corresponding volume data buffer.
	 *
	 * @return data buffer for a given render layer.
	 */
	public boolean isNewVolumeDataAvailable()
	{
		for (final ByteBuffer lByteBuffer : mVolumeDataByteBuffers)
			if (lByteBuffer != null)
				return true;
		return false;
	}

	/**
	 * Returns for a given index the corresponding volume data buffer.
	 *
	 * @return data buffer for a given render layer.
	 */
	public ByteBuffer getVolumeDataBuffer(final int pVolumeDataBufferIndex)
	{
		return mVolumeDataByteBuffers[pVolumeDataBufferIndex];
	}

	/**
	 * Clears volume data buffer.
	 *
	 */
	public void clearVolumeDataBufferReference(final int pVolumeDataBufferIndex)
	{
		mVolumeDataByteBuffers[pVolumeDataBufferIndex] = null;
	}

	/**
	 * Returns object used for locking volume data copy for a given layer.
	 *
	 * @param pRenderLayerIndex
	 *
	 * @return locking object
	 */
	public Object getSetVolumeDataBufferLock(final int pRenderLayerIndex)
	{
		return mSetVolumeDataBufferLocks[pRenderLayerIndex];
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#resetRotationTranslation()
	 */
	@Override
	public void resetRotationTranslation()
	{
		mRotationX = 0;
		mRotationY = 0;
		mTranslationX = 0;
		mTranslationY = 0;
		mTranslationZ = -4;
	}

	@Override
	public void setCurrentRenderLayer(final int pLayerIndex)
	{
		mCurrentRenderLayerIndex = pLayerIndex;
	}

	@Override
	public int getCurrentRenderLayerIndex()
	{
		return mCurrentRenderLayerIndex;
	}

	@Override
	public void setNumberOfRenderLayers(final int pNumberOfRenderLayers)
	{
		mNumberOfRenderLayers = pNumberOfRenderLayers;
	}

	@Override
	public int getNumberOfRenderLayers()
	{
		return mNumberOfRenderLayers;
	}

	/* (non-Javadoc)
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#setVolumeDataBuffer(java.nio.ByteBuffer, long, long, long)
	 */
	@Override
	@Deprecated
	public void setVolumeDataBuffer(final ByteBuffer pByteBuffer,
																	final long pSizeX,
																	final long pSizeY,
																	final long pSizeZ)
	{
		setVolumeDataBuffer(getCurrentRenderLayerIndex(),
												pByteBuffer,
												pSizeX,
												pSizeY,
												pSizeZ);
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#setVolumeDataBuffer(java.nio.ByteBuffer,
	 *      long, long, long)
	 */
	@Override
	public void setVolumeDataBuffer(final int pRenderLayerIndex,
																	final ByteBuffer pByteBuffer,
																	final long pSizeX,
																	final long pSizeY,
																	final long pSizeZ)
	{
		setVolumeDataBuffer(pRenderLayerIndex,
												pByteBuffer,
												pSizeX,
												pSizeY,
												pSizeZ,
												1,
												1,
												1);
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#setVoxelSize(double,
	 *      double, double)
	 */
	@Override
	public void setVoxelSize(	final double pVoxelSizeX,
														final double pVoxelSizeY,
														final double pVoxelSizeZ)
	{
		mVoxelSizeX = pVoxelSizeX;
		mVoxelSizeY = pVoxelSizeY;
		mVoxelSizeZ = pVoxelSizeZ;
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#setVolumeDataBuffer(java.nio.ByteBuffer,
	 *      long, long, long, double, double, double)
	 */
	@Override
	@Deprecated
	public void setVolumeDataBuffer(final ByteBuffer pByteBuffer,
																	final long pSizeX,
																	final long pSizeY,
																	final long pSizeZ,
																	final double pVoxelSizeX,
																	final double pVoxelSizeY,
																	final double pVoxelSizeZ)
	{
		setVolumeDataBuffer(getCurrentRenderLayerIndex(),
												pByteBuffer,
												pSizeX,
												pSizeY,
												pSizeZ,
												pVoxelSizeX,
												pVoxelSizeY,
												pVoxelSizeZ);
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#setVolumeDataBuffer(java.nio.ByteBuffer,
	 *      long, long, long, double, double, double)
	 */
	@Override
	public void setVolumeDataBuffer(final int pRenderLayerIndex,
																	final ByteBuffer pByteBuffer,
																	final long pSizeX,
																	final long pSizeY,
																	final long pSizeZ,
																	final double pVoxelSizeX,
																	final double pVoxelSizeY,
																	final double pVoxelSizeZ)
	{
		synchronized (getSetVolumeDataBufferLock(pRenderLayerIndex))
		{

			if (mVolumeSizeX != pSizeX || mVolumeSizeY != pSizeY
					|| mVolumeSizeZ != pSizeZ)
			{
				mVolumeDimensionsChanged = true;
			}

			mVolumeSizeX = pSizeX;
			mVolumeSizeY = pSizeY;
			mVolumeSizeZ = pSizeZ;

			mVoxelSizeX = pVoxelSizeX;
			mVoxelSizeY = pVoxelSizeY;
			mVoxelSizeZ = pVoxelSizeZ;

			final double lMaxSize = max(max(mVolumeSizeX, mVolumeSizeY),
																	mVolumeSizeZ);

			mScaleX = (float) (pVoxelSizeX * mVolumeSizeX / lMaxSize);
			mScaleY = (float) (pVoxelSizeY * mVolumeSizeY / lMaxSize);
			mScaleZ = (float) (pVoxelSizeZ * mVolumeSizeZ / lMaxSize);

			mVolumeDataByteBuffers[pRenderLayerIndex] = pByteBuffer;

			clearCompletionOfDataBufferCopy(pRenderLayerIndex);
			notifyChangeOfVolumeRenderingParameters();
		}
	}

	@Override
	@Deprecated
	public void setVolumeDataBuffer(final Volume<?> pVolume)
	{
		setVolumeDataBuffer(getCurrentRenderLayerIndex(), pVolume);
	}

	@Override
	public void setVolumeDataBuffer(final int pRenderLayerIndex,
																	final Volume<?> pVolume)
	{
		synchronized (getSetVolumeDataBufferLock(pRenderLayerIndex))
		{
			setVolumeDataBuffer(pRenderLayerIndex,
													pVolume.getDataBuffer(),
													pVolume.getWidthInVoxels(),
													pVolume.getHeightInVoxels(),
													pVolume.getDepthInVoxels(),
													pVolume.getVoxelWidthInRealUnits(),
													pVolume.getVoxelHeightInRealUnits(),
													pVolume.getVoxelDepthInRealUnits());
		}
	}

	@Override
	public VolumeManager createCompatibleVolumeManager(final int pMaxAvailableVolumes)
	{
		return new VolumeManager(pMaxAvailableVolumes);
	}

	/**
	 * Notifies the volume data copy completion.
	 */
	public void notifyCompletionOfDataBufferCopy(final int pRenderLayerIndex)
	{
		mDataBufferCopyIsFinished.set(pRenderLayerIndex, 1);
	}

	/**
	 * Clears data copy buffer flag.
	 */
	public void clearCompletionOfDataBufferCopy(final int pRenderLayerIndex)
	{
		mDataBufferCopyIsFinished.set(pRenderLayerIndex, 0);
	}

	/**
	 * Waits until volume data copy completes all layers.
	 *
	 * @return true is completed, false if it timed-out.
	 */
	@Override
	@Deprecated
	public boolean waitToFinishAllDataBufferCopy(	final long pTimeOut,
																								final TimeUnit pTimeUnit)
	{
		boolean lNoTimeOut = true;
		for (int i = 0; i < getNumberOfRenderLayers(); i++)
			lNoTimeOut &= waitToFinishDataBufferCopy(	getCurrentRenderLayerIndex(),
																								pTimeOut,
																								pTimeUnit);

		return lNoTimeOut;
	}

	/**
	 * Waits until volume data copy completes for current layer.
	 *
	 * @return true is completed, false if it timed-out.
	 */
	@Override
	@Deprecated
	public boolean waitToFinishDataBufferCopy(final long pTimeOut,
																						final TimeUnit pTimeUnit)
	{
		return waitToFinishDataBufferCopy(getCurrentRenderLayerIndex(),
																			pTimeOut,
																			pTimeUnit);

	}

	/**
	 * Waits until volume data copy completes for a given layer
	 *
	 * @return true is completed, false if it timed-out.
	 */
	@Override
	public boolean waitToFinishDataBufferCopy(final int pRenderLayerIndex,
																						final long pTimeOut,
																						final TimeUnit pTimeUnit)

	{
		boolean lNoTimeOut = true;
		final long lStartTimeInNanoseconds = System.nanoTime();
		final long lTimeOutTimeInNanoseconds = lStartTimeInNanoseconds + TimeUnit.NANOSECONDS.convert(pTimeOut,
																																																	pTimeUnit);
		while ((lNoTimeOut = System.nanoTime() < lTimeOutTimeInNanoseconds) && mDataBufferCopyIsFinished.get(pRenderLayerIndex) == 0)
		{
			try
			{
				Thread.sleep(1);
			}
			catch (final InterruptedException e)
			{
				e.printStackTrace();
			}
		}
		return !lNoTimeOut;
	}

	/**
	 * Returns the currently used rotation controller.
	 *
	 * @return currently used rotation controller.
	 */
	public RotationControllerInterface getRotationController()
	{
		return mRotationController;
	}

	/**
	 * Checks whether there is a rotation controller used (in addition to the
	 * mouse).
	 *
	 * @return true if it has a rotation controller
	 */
	public boolean hasRotationController()
	{
		return mRotationController != null ? mRotationController.isActive()
																			: false;
	}

	/**
	 * Interface method implementation
	 *
	 * @see clearvolume.renderer.ClearVolumeRendererInterface#setQuaternionController(clearvolume.controller.RotationControllerInterface)
	 */
	@Override
	public void setQuaternionController(final RotationControllerInterface quaternionController)
	{
		mRotationController = quaternionController;
	}

	/**
	 * Toggles the display of the Control Frame;
	 */
	@Override
	public void toggleControlPanelDisplay()
	{
		if (mControlFrame != null)
			mControlFrame.setVisible(!mControlFrame.isVisible());
	}

	/**
	 * Toggles the display of the Control Frame;
	 */
	@Override
	public void addProcessor(final Processor<?> pProcessor)
	{
		mProcessorsMap.put(pProcessor.getName(), pProcessor);
	}

	/**
	 * Toggles the display of the Control Frame;
	 */
	@Override
	public void addProcessors(final Collection<Processor<?>> pProcessors)
	{
		for (final Processor<?> lProcessor : pProcessors)
			addProcessor(lProcessor);
	}

	/**
	 * Toggles the display of the Control Frame;
	 */
	@Override
	public void addVolumeCaptureListener(final VolumeCaptureListener pVolumeCaptureListener)
	{
		if (pVolumeCaptureListener != null)
			mVolumeCaptureListenerList.add(pVolumeCaptureListener);
	}

	public void notifyVolumeCaptureListeners(	ByteBuffer[] pCaptureBuffer,
																						boolean pFloatType,
																						int pBytesPerVoxel,
																						long pVolumeWidth,
																						long pVolumeHeight,
																						long pVolumeDepth,
																						double pVoxelWidth,
																						double pVoxelHeight,
																						double pVoxelDepth)
	{
		for (final VolumeCaptureListener lVolumeCaptureListener : mVolumeCaptureListenerList)
		{
			lVolumeCaptureListener.capturedVolume(pCaptureBuffer,
																						pFloatType,
																						pBytesPerVoxel,
																						pVolumeWidth,
																						pVolumeHeight,
																						pVolumeDepth,
																						pVoxelWidth,
																						pVoxelHeight,
																						pVoxelDepth);
		}
	}

	/**
	 * Requests capture of the current displayed volume (Preferably of all layers
	 * but possibly just of the current layer.)
	 */
	@Override
	public void requestVolumeCapture()
	{
		mVolumeCaptureFlag = true;
		requestDisplay();
	};

	@Override
	public void setMultiPass(boolean pMultiPassOn)
	{
		if (mAdaptiveLODController != null)
			mAdaptiveLODController.setActive(pMultiPassOn);

	}

	/**
	 * Returns the Adaptive level-of-detail(LOD) controller.
	 * 
	 * @return LOD controller
	 */
	@Override
	public AdaptiveLODController getAdaptiveLODController()
	{
		return mAdaptiveLODController;
	}

	/**
	 * Clamps the value pValue to e interval [pMin,pMax]
	 *
	 * @param pValue
	 *          to be clamped
	 * @param pMin
	 *          minimum
	 * @param pMax
	 *          maximum
	 * @return clamped value
	 */
	public static double clamp(	final double pValue,
															final double pMin,
															final double pMax)
	{
		return Math.min(Math.max(pValue, pMin), pMax);
	}

	@Override
	public void close()
	{
		if (mControlFrame != null)
			try
			{
				SwingUtilities.invokeAndWait(new Runnable()
				{

					@Override
					public void run()
					{
						if (mControlFrame != null)
							try
							{
								mControlFrame.dispose();
								mControlFrame = null;
							}
							catch (final Throwable e)
							{
								e.printStackTrace();
							}
					}
				});
			}
			catch (final Throwable e)
			{
				e.printStackTrace();
			}

	}

}