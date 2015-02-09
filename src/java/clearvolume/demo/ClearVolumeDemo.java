package clearvolume.demo;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.junit.Test;

import clearvolume.controller.ExternalRotationController;
import clearvolume.projections.ProjectionAlgorithm;
import clearvolume.renderer.ClearVolumeRendererInterface;
import clearvolume.renderer.clearcuda.JCudaClearVolumeRenderer;
import clearvolume.renderer.factory.ClearVolumeRendererFactory;
import clearvolume.renderer.jogl.overlay.o2d.GraphOverlay;
import clearvolume.renderer.jogl.overlay.o3d.PathOverlay;
import clearvolume.renderer.processors.Processor;
import clearvolume.renderer.processors.ProcessorResultListener;
import clearvolume.renderer.processors.impl.CUDAProcessorTest;
import clearvolume.renderer.processors.impl.OpenCLTenengrad;
import clearvolume.renderer.processors.impl.OpenCLTest;
import clearvolume.transferf.TransferFunctions;

import com.jogamp.newt.awt.NewtCanvasAWT;

public class ClearVolumeDemo
{

	private static ClearVolumeRendererInterface mClearVolumeRenderer;

	public static void main(String[] argv) throws ClassNotFoundException
	{
		if (argv.length == 0)
		{
			Class<?> c = Class.forName("clearvolume.demo.ClearVolumeDemo");

			System.out.println("Give one of the following method names as parameter:");

			for (Member m : c.getMethods())
			{
				String name = ((Method) m).getName();

				if (name.substring(0, 4).equals("demo"))
				{
					System.out.println("Demo: " + ((Method) m).getName());
				}
			}
		}
		else
		{
			ClearVolumeDemo cvdemo = new ClearVolumeDemo();
			Method m;

			try
			{
				m = cvdemo.getClass().getMethod(argv[0]);
			}
			catch (Exception e)
			{
				System.out.println("Could not launch " + argv[0]
														+ " because ...");
				e.printStackTrace();

				return;
			}

			try
			{
				System.out.println("Running " + argv[0] + "()...");
				m.invoke(cvdemo);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

	}

	@Test
	public void demoOpenCLProcessors() throws InterruptedException,
																		IOException
	{

		final ClearVolumeRendererInterface lClearVolumeRenderer = ClearVolumeRendererFactory.newOpenCLRenderer(	"ClearVolumeTest",
																																																						1024,
																																																						1024,
																																																						1,
																																																						512,
																																																						512,
																																																						1,
																																																						false);
		lClearVolumeRenderer.addOverlay(new PathOverlay());
		lClearVolumeRenderer.addProcessor(new CUDAProcessorTest());

		OpenCLTest myProc = new OpenCLTest();
		myProc.addResultListener(new ProcessorResultListener<Double>()
		{

			@Override
			public void notifyResult(	Processor<Double> pSource,
																Double pResult)
			{
				System.out.println(pResult);
			}
		});

		lClearVolumeRenderer.addProcessor(myProc);

		lClearVolumeRenderer.setTransferFunction(TransferFunctions.getGrayLevel());
		lClearVolumeRenderer.setVisible(true);

		final int lResolutionX = 256;
		final int lResolutionY = lResolutionX;
		final int lResolutionZ = lResolutionX;

		final byte[] lVolumeDataArray = new byte[lResolutionX * lResolutionY
																							* lResolutionZ];

		for (int z = 0; z < lResolutionZ; z++)
			for (int y = 0; y < lResolutionY; y++)
				for (int x = 0; x < lResolutionX; x++)
				{
					final int lIndex = x + lResolutionX
															* y
															+ lResolutionX
															* lResolutionY
															* z;
					int lCharValue = (((byte) x ^ (byte) y ^ (byte) z));
					if (lCharValue < 12)
						lCharValue = 0;
					lVolumeDataArray[lIndex] = (byte) lCharValue;
				}

		lClearVolumeRenderer.setCurrentRenderLayer(0);
		lClearVolumeRenderer.setVolumeDataBuffer(	ByteBuffer.wrap(lVolumeDataArray),
																							lResolutionX,
																							lResolutionY,
																							lResolutionZ);
		lClearVolumeRenderer.requestDisplay();

		while (lClearVolumeRenderer.isShowing())
		{
			Thread.sleep(500);
		}

		lClearVolumeRenderer.close();
	}

	@Test
	public void demoOpenCLTenengrad()	throws InterruptedException,
																		IOException
	{

		final ClearVolumeRendererInterface lClearVolumeRenderer = ClearVolumeRendererFactory.newOpenCLRenderer(	"ClearVolumeTest",
																																																						512,
																																																						512,
																																																						1,
																																																						512,
																																																						512,
																																																						1,
																																																						false);
		lClearVolumeRenderer.addOverlay(new PathOverlay());
		lClearVolumeRenderer.addProcessor(new CUDAProcessorTest());

		OpenCLTenengrad tenengradProc = new OpenCLTenengrad();
		tenengradProc.addResultListener(new ProcessorResultListener<Double>()
		{

			@Override
			public void notifyResult(	Processor<Double> pSource,
																Double pResult)
			{
				System.out.println("tenengrad: " + pResult);
			}
		});

		lClearVolumeRenderer.addProcessor(tenengradProc);

		lClearVolumeRenderer.setTransferFunction(TransferFunctions.getGrayLevel());
		lClearVolumeRenderer.setVisible(true);

		final int lResolutionX = 256;
		final int lResolutionY = lResolutionX;
		final int lResolutionZ = lResolutionX;

		final byte[] lVolumeDataArray = new byte[lResolutionX * lResolutionY
																							* lResolutionZ];

		for (int z = 0; z < lResolutionZ; z++)
			for (int y = 0; y < lResolutionY; y++)
				for (int x = 0; x < lResolutionX; x++)
				{
					final int lIndex = x + lResolutionX
															* y
															+ lResolutionX
															* lResolutionY
															* z;
					int lCharValue = (((byte) x ^ (byte) y ^ (byte) z));
					if (lCharValue < 12)
						lCharValue = 0;

					lVolumeDataArray[lIndex] = (byte) lCharValue;
					// lVolumeDataArray[lIndex] = (byte) (255 * x
					// * x
					// / lResolutionX / lResolutionX);

				}

		lClearVolumeRenderer.setCurrentRenderLayer(0);
		lClearVolumeRenderer.setVolumeDataBuffer(	ByteBuffer.wrap(lVolumeDataArray),
																							lResolutionX,
																							lResolutionY,
																							lResolutionZ);
		lClearVolumeRenderer.requestDisplay();

		double s = 0;
		while (lClearVolumeRenderer.isShowing())
		{

			Thread.sleep(500);

			tenengradProc.setSigma(s);
			s += .5;

			lClearVolumeRenderer.setVolumeDataBuffer(	ByteBuffer.wrap(lVolumeDataArray),
																								lResolutionX,
																								lResolutionY,
																								lResolutionZ);
			lClearVolumeRenderer.requestDisplay();

		}

		lClearVolumeRenderer.close();
	}

	@Test
	public void demoCudaProcessors() throws InterruptedException,
																	IOException
	{

		final ClearVolumeRendererInterface lClearVolumeRenderer = ClearVolumeRendererFactory.newCudaRenderer(	"ClearVolumeTest",
																																																					1024,
																																																					1024,
																																																					1,
																																																					512,
																																																					512,
																																																					1,
																																																					false);
		lClearVolumeRenderer.addOverlay(new PathOverlay());
		lClearVolumeRenderer.addProcessor(new CUDAProcessorTest());
		lClearVolumeRenderer.addProcessor(new OpenCLTest());

		lClearVolumeRenderer.setTransferFunction(TransferFunctions.getGrayLevel());
		lClearVolumeRenderer.setVisible(true);

		final int lResolutionX = 256;
		final int lResolutionY = lResolutionX;
		final int lResolutionZ = lResolutionX;

		final byte[] lVolumeDataArray = new byte[lResolutionX * lResolutionY
																							* lResolutionZ];

		for (int z = 0; z < lResolutionZ; z++)
			for (int y = 0; y < lResolutionY; y++)
				for (int x = 0; x < lResolutionX; x++)
				{
					final int lIndex = x + lResolutionX
															* y
															+ lResolutionX
															* lResolutionY
															* z;
					int lCharValue = (((byte) x ^ (byte) y ^ (byte) z));
					if (lCharValue < 12)
						lCharValue = 0;
					lVolumeDataArray[lIndex] = (byte) lCharValue;
				}

		lClearVolumeRenderer.setCurrentRenderLayer(0);
		lClearVolumeRenderer.setVolumeDataBuffer(	ByteBuffer.wrap(lVolumeDataArray),
																							lResolutionX,
																							lResolutionY,
																							lResolutionZ);
		lClearVolumeRenderer.requestDisplay();

		while (lClearVolumeRenderer.isShowing())
		{
			Thread.sleep(500);
		}

		lClearVolumeRenderer.close();
	}

	@Test
	public void demoOverlay2D()	throws InterruptedException,
															IOException
	{

		final ClearVolumeRendererInterface lClearVolumeRenderer = ClearVolumeRendererFactory.newBestRenderer(	"ClearVolumeTest",
																																																					1024,
																																																					1024,
																																																					1,
																																																					512,
																																																					512,
																																																					1,
																																																					false);

		GraphOverlay lGraphOverlay = new GraphOverlay(1024);
		lClearVolumeRenderer.addOverlay(lGraphOverlay);

		lClearVolumeRenderer.setTransferFunction(TransferFunctions.getGrayLevel());
		lClearVolumeRenderer.setVisible(true);

		final int lResolutionX = 256;
		final int lResolutionY = lResolutionX;
		final int lResolutionZ = lResolutionX;

		final byte[] lVolumeDataArray = new byte[lResolutionX * lResolutionY
																							* lResolutionZ];

		for (int z = 0; z < lResolutionZ; z++)
			for (int y = 0; y < lResolutionY; y++)
				for (int x = 0; x < lResolutionX; x++)
				{
					final int lIndex = x + lResolutionX
															* y
															+ lResolutionX
															* lResolutionY
															* z;
					int lCharValue = (((byte) x ^ (byte) y ^ (byte) z));
					if (lCharValue < 12)
						lCharValue = 0;
					lVolumeDataArray[lIndex] = (byte) lCharValue;
				}

		lClearVolumeRenderer.setCurrentRenderLayer(0);
		lClearVolumeRenderer.setVolumeDataBuffer(	ByteBuffer.wrap(lVolumeDataArray),
																							lResolutionX,
																							lResolutionY,
																							lResolutionZ);
		lClearVolumeRenderer.requestDisplay();


		while (lClearVolumeRenderer.isShowing())
		{
			Thread.sleep(10);
			double lValue = 0.5 + 0.5 * Math.random();
			lGraphOverlay.addPoint(lValue);
		}

		lClearVolumeRenderer.close();
	}

	@Test
	public void demoOverlay3D()	throws InterruptedException,
															IOException
	{
		final ClearVolumeRendererInterface lClearVolumeRenderer = ClearVolumeRendererFactory.newBestRenderer(	"ClearVolumeTest",
																																																					1024,
																																																					1024,
																																																					1,
																																																					512,
																																																					512,
																																																					1,
																																																					false);
		lClearVolumeRenderer.addOverlay(new PathOverlay());

		lClearVolumeRenderer.setTransferFunction(TransferFunctions.getGrayLevel());
		lClearVolumeRenderer.setVisible(true);

		final int lResolutionX = 256;
		final int lResolutionY = lResolutionX;
		final int lResolutionZ = lResolutionX;

		final byte[] lVolumeDataArray = new byte[lResolutionX * lResolutionY
																							* lResolutionZ];

		for (int z = 0; z < lResolutionZ; z++)
			for (int y = 0; y < lResolutionY; y++)
				for (int x = 0; x < lResolutionX; x++)
				{
					final int lIndex = x + lResolutionX
															* y
															+ lResolutionX
															* lResolutionY
															* z;
					int lCharValue = (((byte) x ^ (byte) y ^ (byte) z));
					if (lCharValue < 12)
						lCharValue = 0;
					lVolumeDataArray[lIndex] = (byte) lCharValue;
				}

		lClearVolumeRenderer.setCurrentRenderLayer(0);
		lClearVolumeRenderer.setVolumeDataBuffer(	ByteBuffer.wrap(lVolumeDataArray),
																							lResolutionX,
																							lResolutionY,
																							lResolutionZ);
		lClearVolumeRenderer.requestDisplay();

		while (lClearVolumeRenderer.isShowing())
		{
			Thread.sleep(500);
		}

		lClearVolumeRenderer.close();
	}

	@Test
	public void demoRendererInJFrame() throws InterruptedException,
																		IOException
	{

		final ClearVolumeRendererInterface lClearVolumeRenderer = ClearVolumeRendererFactory.newBestRenderer(	"ClearVolumeTest",
																																																					1024,
																																																					1024,
																																																					1,
																																																					512,
																																																					512,
																																																					1,
																																																					true);
		NewtCanvasAWT lNewtCanvasAWT = lClearVolumeRenderer.getNewtCanvasAWT();

		final JFrame lJFrame = new JFrame("ClearVolume");
		lJFrame.setLayout(new BorderLayout());
		final Container lContainer = new Container();
		lContainer.setLayout(new BorderLayout());
		lContainer.add(lNewtCanvasAWT, BorderLayout.CENTER);
		lJFrame.setSize(new Dimension(1024, 1024));
		lJFrame.add(lContainer);
		SwingUtilities.invokeLater(new Runnable()
		{
			@Override
			public void run()
			{
				lJFrame.setVisible(true);
			}
		});

		lClearVolumeRenderer.setTransferFunction(TransferFunctions.getGrayLevel());
		lClearVolumeRenderer.setVisible(true);

		final int lResolutionX = 256;
		final int lResolutionY = lResolutionX;
		final int lResolutionZ = lResolutionX;

		final byte[] lVolumeDataArray = new byte[lResolutionX * lResolutionY
																							* lResolutionZ];

		for (int z = 0; z < lResolutionZ; z++)
			for (int y = 0; y < lResolutionY; y++)
				for (int x = 0; x < lResolutionX; x++)
				{
					final int lIndex = x + lResolutionX
															* y
															+ lResolutionX
															* lResolutionY
															* z;
					int lCharValue = (((byte) x ^ (byte) y ^ (byte) z));
					if (lCharValue < 12)
						lCharValue = 0;
					lVolumeDataArray[lIndex] = (byte) lCharValue;
				}

		lClearVolumeRenderer.setCurrentRenderLayer(0);
		lClearVolumeRenderer.setVolumeDataBuffer(	ByteBuffer.wrap(lVolumeDataArray),
																							lResolutionX,
																							lResolutionY,
																							lResolutionZ);
		lClearVolumeRenderer.requestDisplay();

		while (lClearVolumeRenderer.isShowing())
		{
			Thread.sleep(100);
			lJFrame.setTitle("BRAVO! THIS IS A JFRAME! It WORKS!");
		}

		lClearVolumeRenderer.close();
	}

	@Test
	public void demoWith8BitGeneratedDataset() throws InterruptedException,
																						IOException
	{

		final ClearVolumeRendererInterface lClearVolumeRenderer = ClearVolumeRendererFactory.newBestRenderer(	"ClearVolumeTest",
																																																					1024,
																																																					1024,
																																																					1,
																																																					512,
																																																					512,
																																																					1,
																																																					false);
		lClearVolumeRenderer.setTransferFunction(TransferFunctions.getGrayLevel());
		lClearVolumeRenderer.setVisible(true);

		final int lResolutionX = 256;
		final int lResolutionY = lResolutionX;
		final int lResolutionZ = lResolutionX;

		final byte[] lVolumeDataArray = new byte[lResolutionX * lResolutionY
																							* lResolutionZ];

		for (int z = 0; z < lResolutionZ; z++)
			for (int y = 0; y < lResolutionY; y++)
				for (int x = 0; x < lResolutionX; x++)
				{
					final int lIndex = x + lResolutionX
															* y
															+ lResolutionX
															* lResolutionY
															* z;
					int lCharValue = (((byte) x ^ (byte) y ^ (byte) z));
					if (lCharValue < 12)
						lCharValue = 0;
					lVolumeDataArray[lIndex] = (byte) lCharValue;
				}

		lClearVolumeRenderer.setCurrentRenderLayer(0);
		lClearVolumeRenderer.setVolumeDataBuffer(	ByteBuffer.wrap(lVolumeDataArray),
																							lResolutionX,
																							lResolutionY,
																							lResolutionZ);
		lClearVolumeRenderer.requestDisplay();

		while (lClearVolumeRenderer.isShowing())
		{
			Thread.sleep(500);
		}

		lClearVolumeRenderer.close();
	}

	@Test
	public void demoAspectRatio()	throws InterruptedException,
																IOException
	{
		final ClearVolumeRendererInterface lClearVolumeRenderer = new JCudaClearVolumeRenderer(	"ClearVolumeTest",
																																														512,
																																														512,
																																														1,
																																														512,
																																														512,
																																														1,
																																														false);
		lClearVolumeRenderer.setTransferFunction(TransferFunctions.getGrayLevel());
		lClearVolumeRenderer.setVisible(true);

		final int lResolutionX = 128;
		final int lResolutionY = 128;
		final int lResolutionZ = 128;

		final byte[] lVolumeDataArray = new byte[lResolutionX * lResolutionY
																							* lResolutionZ];

		for (int z = 0; z < lResolutionZ; z++)
			for (int y = 0; y < lResolutionY; y++)
				for (int x = 0; x < lResolutionX; x++)
				{
					final int lIndex = x + lResolutionX
															* y
															+ lResolutionX
															* lResolutionY
															* z;
					int lCharValue = (((byte) x ^ (byte) y ^ (byte) z));
					if (lCharValue < 12)
						lCharValue = 0;

					lVolumeDataArray[lIndex] = (byte) lCharValue;
				}

		lClearVolumeRenderer.setCurrentRenderLayer(0);
		lClearVolumeRenderer.setVolumeDataBuffer(	ByteBuffer.wrap(lVolumeDataArray),
																							lResolutionX,
																							lResolutionY,
																							lResolutionZ);

		lClearVolumeRenderer.requestDisplay();

		while (lClearVolumeRenderer.isShowing())
		{
			Thread.sleep(500);
		}

		lClearVolumeRenderer.close();
	}

	@Test
	public void demoAspectRatioPreset()	throws InterruptedException,
																			IOException
	{
		final ClearVolumeRendererInterface lClearVolumeRenderer = new JCudaClearVolumeRenderer(	"ClearVolumeTest",
																																														512,
																																														512,
																																														1,
																																														512,
																																														512,
																																														1,
																																														false);
		lClearVolumeRenderer.setTransferFunction(TransferFunctions.getGrayLevel());
		lClearVolumeRenderer.setVisible(true);

		final int lResolutionX = 400;
		final int lResolutionY = 100;
		final int lResolutionZ = 200;

		final byte[] lVolumeDataArray = new byte[lResolutionX * lResolutionY
																							* lResolutionZ];

		for (int z = 0; z < lResolutionZ; z++)
			for (int y = 0; y < lResolutionY; y++)
				for (int x = 0; x < lResolutionX; x++)
				{
					final int lIndex = x + lResolutionX
															* y
															+ lResolutionX
															* lResolutionY
															* z;
					int lCharValue = (((byte) x ^ (byte) y ^ (byte) z));
					if (lCharValue < 12)
						lCharValue = 0;

					lVolumeDataArray[lIndex] = (byte) lCharValue;
				}

		lClearVolumeRenderer.setCurrentRenderLayer(0);
		lClearVolumeRenderer.setVolumeDataBuffer(	ByteBuffer.wrap(lVolumeDataArray),
																							lResolutionX,
																							lResolutionY,
																							lResolutionZ);

		lClearVolumeRenderer.setVoxelSize(lResolutionX * 5.0,
																			lResolutionY * 4.0,
																			lResolutionZ * 3.0);
		lClearVolumeRenderer.requestDisplay();

		while (lClearVolumeRenderer.isShowing())
		{
			Thread.sleep(500);
		}

		lClearVolumeRenderer.close();
	}

	@Test
	public void demoWith16BitGeneratedDataset()	throws InterruptedException,
																							IOException
	{
		final ClearVolumeRendererInterface lClearVolumeRenderer = ClearVolumeRendererFactory.newBestRenderer(	"ClearVolumeTest",
																																																					768,
																																																					768,
																																																					2,
																																																					false);
		lClearVolumeRenderer.setTransferFunction(TransferFunctions.getGrayLevel());
		lClearVolumeRenderer.setVisible(true);

		final int lResolutionX = 256;
		final int lResolutionY = 256;
		final int lResolutionZ = 256;

		final byte[] lVolumeDataArray = new byte[lResolutionX * lResolutionY
																							* lResolutionZ
																							* 2];

		for (int z = 0; z < lResolutionZ; z++)
			for (int y = 0; y < lResolutionY; y++)
				for (int x = 0; x < lResolutionX; x++)
				{
					final int lIndex = 2 * (x + lResolutionX * y + lResolutionX * lResolutionY
																													* z);
					lVolumeDataArray[lIndex + 1] = (byte) (((byte) x ^ (byte) y ^ (byte) z));
				}

		lClearVolumeRenderer.setVolumeDataBuffer(	ByteBuffer.wrap(lVolumeDataArray),
																							lResolutionX,
																							lResolutionY,
																							lResolutionZ);
		lClearVolumeRenderer.requestDisplay();

		while (lClearVolumeRenderer.isShowing())
		{
			Thread.sleep(100);
		}

		lClearVolumeRenderer.close();

	}

	@Test
	public void demoWithGeneratedDatasetWithEgg3D()	throws InterruptedException,
																									IOException
	{

		final ClearVolumeRendererInterface lClearVolumeRenderer = ClearVolumeRendererFactory.newBestRenderer(	"ClearVolumeTest",
																																																					512,
																																																					512,
																																																					false);
		lClearVolumeRenderer.setTransferFunction(TransferFunctions.getGrayLevel());
		lClearVolumeRenderer.setVisible(true);
		lClearVolumeRenderer.setProjectionAlgorithm(ProjectionAlgorithm.MaxProjection);

		ExternalRotationController lEgg3DController = null;
		try
		{
			lEgg3DController = new ExternalRotationController(ExternalRotationController.cDefaultEgg3DTCPport,
																												lClearVolumeRenderer);
			lClearVolumeRenderer.setQuaternionController(lEgg3DController);
			lEgg3DController.connectAsynchronouslyOrWait();
		}
		catch (final Exception e)
		{
			e.printStackTrace();
		}

		final int lResolutionX = 128;
		final int lResolutionY = lResolutionX;
		final int lResolutionZ = lResolutionX;

		final byte[] lVolumeDataArray = new byte[lResolutionX * lResolutionY
																							* lResolutionZ];

		for (int z = 0; z < lResolutionZ; z++)
			for (int y = 0; y < lResolutionY; y++)
				for (int x = 0; x < lResolutionX; x++)
				{
					final int lIndex = x + lResolutionX
															* y
															+ lResolutionX
															* lResolutionY
															* z;
					lVolumeDataArray[lIndex] = (byte) (x ^ y ^ z);
				}

		final ByteBuffer lWrappedArray = ByteBuffer.wrap(lVolumeDataArray);
		lClearVolumeRenderer.setVolumeDataBuffer(	lWrappedArray,
																							lResolutionX,
																							lResolutionY,
																							lResolutionZ);

		lClearVolumeRenderer.requestDisplay();

		while (lClearVolumeRenderer.isShowing())
		{
			Thread.sleep(100);
		}

		lClearVolumeRenderer.close();

		if (lEgg3DController != null)
			lEgg3DController.close();

	}

	@Test
	public void demoWithFileDatasets()
	{

		try
		{
			startSample("./data/Bucky.raw", 1, 32, 32, 32);
		}
		catch (final Throwable e)
		{
			e.printStackTrace();
		}

	}

	@Test
	public void demoWith8BitGeneratedDataset2Layers()	throws InterruptedException,
																										IOException
	{
		final ClearVolumeRendererInterface lClearVolumeRenderer = new JCudaClearVolumeRenderer(	"ClearVolumeTest",
																																														512,
																																														512,
																																														1,
																																														512,
																																														512,
																																														2,
																																														false);

		lClearVolumeRenderer.setVisible(true);

		final int lResolutionX = 256;
		final int lResolutionY = lResolutionX;
		final int lResolutionZ = lResolutionX;

		final byte[] lVolumeDataArray0 = new byte[lResolutionX * lResolutionY
																							* lResolutionZ];

		for (int z = 0; z < lResolutionZ; z++)
			for (int y = 0; y < lResolutionY; y++)
				for (int x = 0; x < lResolutionX; x++)
				{
					final int lIndex = x + lResolutionX
															* y
															+ lResolutionX
															* lResolutionY
															* z;
					int lCharValue = (((byte) x ^ (byte) y ^ (byte) z));
					if (lCharValue < 12)
						lCharValue = 0;
					lVolumeDataArray0[lIndex] = (byte) lCharValue;
				}

		lClearVolumeRenderer.setCurrentRenderLayer(0);
		lClearVolumeRenderer.setVolumeDataBuffer(	ByteBuffer.wrap(lVolumeDataArray0),
																							lResolutionX,
																							lResolutionY,
																							lResolutionZ);

		lClearVolumeRenderer.requestDisplay();
		Thread.sleep(2000);

		final byte[] lVolumeDataArray1 = new byte[lResolutionX * lResolutionY
																							* lResolutionZ];

		for (int z = 0; z < lResolutionZ / 2; z++)
			for (int y = 0; y < lResolutionY / 2; y++)
				for (int x = 0; x < lResolutionX / 2; x++)
				{
					final int lIndex = x + lResolutionX
															* y
															+ lResolutionX
															* lResolutionY
															* z;
					int lCharValue = 255 - (((byte) (x) ^ (byte) (y) ^ (byte) z));
					if (lCharValue < 12)
						lCharValue = 0;
					lVolumeDataArray1[lIndex] = (byte) (lCharValue);
				}

		lClearVolumeRenderer.setCurrentRenderLayer(1);
		lClearVolumeRenderer.setVolumeDataBuffer(	ByteBuffer.wrap(lVolumeDataArray1),
																							lResolutionX,
																							lResolutionY,
																							lResolutionZ);/**/

		lClearVolumeRenderer.requestDisplay();

		while (lClearVolumeRenderer.isShowing())
		{
			Thread.sleep(500);
		}

		lClearVolumeRenderer.close();
	}

	private static void startSample(final String pRessourceName,
																	final int pBytesPerVoxel,
																	final int pSizeX,
																	final int pSizeY,
																	final int pSizeZ)	throws IOException,
																										InterruptedException
	{
		final InputStream lResourceAsStream = ClearVolumeDemo.class.getResourceAsStream(pRessourceName);
		startSample(lResourceAsStream,
								pBytesPerVoxel,
								pSizeX,
								pSizeY,
								pSizeZ);
	}

	private static void startSample(final InputStream pInputStream,
																	final int pBytesPerVoxel,
																	final int pSizeX,
																	final int pSizeY,
																	final int pSizeZ)	throws IOException,
																										InterruptedException
	{

		final byte[] data = loadData(	pInputStream,
																	pBytesPerVoxel,
																	pSizeX,
																	pSizeY,
																	pSizeZ);

		mClearVolumeRenderer = ClearVolumeRendererFactory.newBestRenderer("ClearVolumeTest",
																																			512,
																																			512,
																																			pBytesPerVoxel,
																																			false);

		mClearVolumeRenderer.setTransferFunction(TransferFunctions.getRainbow());
		mClearVolumeRenderer.setVisible(true);

		mClearVolumeRenderer.setVolumeDataBuffer(	ByteBuffer.wrap(data),
																							pSizeX,
																							pSizeY,
																							pSizeZ);

		mClearVolumeRenderer.requestDisplay();

		while (mClearVolumeRenderer.isShowing())
		{
			Thread.sleep(100);
		}

	}

	private static byte[] loadData(	final String pRessourceName,
																	final int pBytesPerVoxel,
																	final int sizeX,
																	final int sizeY,
																	final int sizeZ) throws IOException
	{
		final InputStream lResourceAsStream = ClearVolumeDemo.class.getResourceAsStream(pRessourceName);

		return loadData(lResourceAsStream,
										pBytesPerVoxel,
										sizeX,
										sizeY,
										sizeZ);
	}

	private static byte[] loadData(	final InputStream pInputStream,
																	final int pBytesPerVoxel,
																	final int sizeX,
																	final int sizeY,
																	final int sizeZ) throws IOException
	{
		// Try to read the specified file
		byte data[] = null;
		final InputStream fis = pInputStream;
		try
		{
			final int size = pBytesPerVoxel * sizeX * sizeY * sizeZ;
			data = new byte[size];
			fis.read(data);
		}
		catch (final IOException e)
		{
			System.err.println("Could not load input file");
			e.printStackTrace();
			return null;
		}
		fis.close();
		return data;
	}

}
