package bdv.jogl.VolumeRenderer.Scene;

import java.util.ArrayList;
import java.util.List;

import com.jogamp.opengl.math.geom.AABBox;

import bdv.jogl.VolumeRenderer.Camera;
import bdv.jogl.VolumeRenderer.ShaderPrograms.MultiVolumeRenderer;
import bdv.jogl.VolumeRenderer.gui.GLWindow.GLWindow;
import bdv.jogl.VolumeRenderer.utils.VolumeDataBlock;
import bdv.jogl.VolumeRenderer.utils.VolumeDataManager;
import static bdv.jogl.VolumeRenderer.utils.VolumeDataUtils.calcEyeAndCenterByGivenHull;;

public class InteraktionAnimator {
	
	private final MultiVolumeRenderer renderer;
	
	private final GLWindow renderWindow;
	
	private int currentAnimationPercentage =0;
	
	private final int percentageIncrement = 5;
	
	private Thread initAnimationThread = null;
	
	private Thread motionToTargetThread = null;
	
	
	private final VolumeDataManager manager;
	
	/**
	 * Animation constructor
	 * @param renderer
	 * @param renderWindow
	 */
	public InteraktionAnimator(final MultiVolumeRenderer renderer, final GLWindow renderWindow, final VolumeDataManager manager){
		this.renderer = renderer;
		this.renderWindow = renderWindow;
		this.manager =manager; 
	}
	
	/**
	 * stop init animation thread
	 */
	public void interruptInitAnimation(){
		if(initAnimationThread == null){
			return;
		}
		initAnimationThread.interrupt();
	};
	


	/**
	 * blending of 3D on 3D
	 */
	private void doInitAnimationStep(){
		
		renderer.setOpacity3D((float)(currentAnimationPercentage)/ 100f);
		renderWindow.getGlCanvas().repaint();

	}
	
	/**
	 * Starts the big volume in fading 
	 */
	public void startInitAnimation(){
		if(initAnimationThread!= null){
			if(initAnimationThread.isAlive()){
				return;
			}
			initAnimationThread = null;
		}
		initAnimationThread = new Thread(){
				public void run(){
					for(currentAnimationPercentage =0; currentAnimationPercentage< 100; currentAnimationPercentage+=percentageIncrement){
						doInitAnimationStep();
						try {
							sleep(100);
						} catch (InterruptedException e) {
							break;
						}
					}
					
					//make 100% animation mode
					currentAnimationPercentage = 100;
					doInitAnimationStep();
						
				}
		};
		
		initAnimationThread.start();
	}
	
	
	/**
	 * Motion to data and update partial data
	 * @param hullVolume
	 * @param partialVolumesInHullVolume
	 * @param time
	 */
	public void startMoveToSelectionAnimation( final AABBox hullVolume,
			final List<VolumeDataBlock> partialVolumesInHullVolume, final int time){
		if(null != this.motionToTargetThread && motionToTargetThread.isAlive()){
			return;
		}
		
		motionToTargetThread = null;
		final List<float[][]> motionPositions = calcEyeAndCenterPath(hullVolume, 100 /percentageIncrement);
		motionToTargetThread = new Thread(){
			public void run(){
				boolean updatedData= false;
				Camera c = renderWindow.getScene().getCamera();
				AABBox currentHullVolume = renderer.getDrawRect();
				int n = 0;
				for(currentAnimationPercentage = 0; currentAnimationPercentage < 100; currentAnimationPercentage+=percentageIncrement){
					try {
						float eyeCenter[][] = motionPositions.get(n);
						//enter hull volume -> invalid fragments
						/*if(currentAnimationPercentage == 70){
							renderer.setDrawRect(hullVolume);
							System.out.println("Done");
							updatedData=true;
						}*/
						c.setEyePoint(eyeCenter[0]);
						c.setLookAtPoint(eyeCenter[1]);
						c.updateViewMatrix();
						n++;
						sleep(100);
						
					} catch (InterruptedException e) {
						break;
					}
				}
				currentAnimationPercentage = 100;
				
				renderer.setUseSparseVolumes(true);
				renderer.setDrawRect(hullVolume);
				
				for(int i =0; i < partialVolumesInHullVolume.size(); i++){
					manager.forceVolumeUpdate(i, time, partialVolumesInHullVolume.get(i));
				}
				renderWindow.getScene().getCamera().centerOnBox(hullVolume);
				renderWindow.getGlCanvas().repaint();
			}
		};
		motionToTargetThread.start();
	}
	
	/**
	 * cancel move to animation and shows result
	 */
	public void interruptMoveToSelectionAnimation(){
		if(motionToTargetThread == null){
			return;
		}
		motionToTargetThread.interrupt();
	}
	
	/**
	 * stops all running animation threads 
	 */
	public void stopAllAnimations() {
		interruptInitAnimation();
		
	}
	
	/**
	 * calculates the positions of eye and center on a n steps path to view hullVolume
	 * @param hullVolume
	 * @param n 
	 * @return
	 */
	private List<float[][]> calcEyeAndCenterPath(AABBox hullVolume,int n){
		List<float[][]> positions = new ArrayList<float[][]>();
		float eyeCenterStart[][] = new float[][]{renderWindow.getScene().getCamera().getEyePoint(),renderWindow.getScene().getCamera().getLookAtPoint()};
		float eyeCenterFinal[][] = calcEyeAndCenterByGivenHull(hullVolume);
		
		//TODO non linear
		float stepMovesEyeCenter [][]= new float[2][3];
		for(int p = 0;p < stepMovesEyeCenter.length; p++){
			for(int d = 0; d < stepMovesEyeCenter[p].length; d++){
				stepMovesEyeCenter[p][d] = (eyeCenterFinal[p][d] - eyeCenterStart[p][d]) / (float)n;
			}
		}
		
		//linear steps
		float currentPos[][] = eyeCenterStart.clone();
		for(int i=0; i < n; i++){
			for(int p = 0;p < stepMovesEyeCenter.length; p++){
				for(int d = 0; d < stepMovesEyeCenter[p].length; d++){
					currentPos[p][d] += stepMovesEyeCenter[p][d];
					
				}
			}
			float savePosition[][] = new float[][]{currentPos[0].clone(),currentPos[1].clone()};
			positions.add(savePosition);
		}
		//TODO end
		
		return positions;
	}
}