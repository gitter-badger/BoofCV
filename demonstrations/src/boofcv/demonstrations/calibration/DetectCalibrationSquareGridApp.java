/*
 * Copyright (c) 2011-2015, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.demonstrations.calibration;

import boofcv.abst.fiducial.calib.ConfigSquareGrid;
import boofcv.alg.fiducial.calib.grid.DetectSquareGridFiducial;
import boofcv.alg.fiducial.calib.squares.SquareGrid;
import boofcv.alg.fiducial.calib.squares.SquareNode;
import boofcv.alg.filter.binary.Contour;
import boofcv.factory.calib.FactoryCalibrationTarget;
import boofcv.factory.filter.binary.ThresholdType;
import boofcv.gui.image.ShowImages;
import boofcv.io.UtilIO;
import boofcv.struct.image.ImageFloat32;
import boofcv.struct.image.ImageUInt8;
import georegression.struct.point.Point2D_F64;
import georegression.struct.shapes.Polygon2D_F64;
import org.ddogleg.struct.FastQueue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays detected square grid fiducials.  Visualizes several of its processing steps making it easier to debug.
 *
 * @author Peter Abeles
 */
public class DetectCalibrationSquareGridApp extends CommonDetectCalibrationApp
{
	DetectSquareGridFiducial<ImageFloat32> alg;
	ConfigSquareGrid config;

	public DetectCalibrationSquareGridApp(List<String> exampleInputs) {
		super(exampleInputs);
	}


	public void configure( int numCols , int numRows , double squareWidth, double spaceWidth,
						   boolean forCalibration ) {
		config = new ConfigSquareGrid(numCols,numRows,squareWidth,spaceWidth);

		config.refineWithCorners = forCalibration;

	}

	@Override
	protected boolean process(ImageFloat32 image) {
		if( controlPanel.isManual()) {
			config.thresholding.type = ThresholdType.FIXED;
			config.thresholding.fixedThreshold = controlPanel.getThresholdLevel();
		} else {
			config.thresholding.type = ThresholdType.LOCAL_SQUARE;
		}
		alg = FactoryCalibrationTarget.detectorSquareGrid(config).getAlgorithm();
		return alg.process(image);
	}

	@Override
	protected ImageUInt8 getBinaryImage() {
		return alg.getBinary();
	}

	@Override
	protected List<List<SquareNode>> getClusters() {
		return alg.getClusters();
	}

	@Override
	protected List<Point2D_F64> getCalibrationPoints() {
		return alg.getCalibrationPoints();
	}

	@Override
	protected List<Contour> getContours() {
		return alg.getDetectorSquare().getAllContours();
	}

	@Override
	protected FastQueue<Polygon2D_F64> getFoundPolygons() {
		return alg.getDetectorSquare().getFoundPolygons();
	}

	@Override
	protected List<SquareGrid> getGrids() {
		return alg.getGrids().getGrids();
	}

	public static void main(String[] args) {

		List<String>  examples = new ArrayList<String>();

		for (int i = 1; i <= 11; i++) {
			examples.add(UtilIO.pathExample(String.format("calibration/stereo/Bumblebee2_Square/left%02d.jpg", i)));
		}

		DetectCalibrationSquareGridApp app = new DetectCalibrationSquareGridApp(examples);

		app.configure(5,7,1,1,false);

		app.openFile(new File(examples.get(0)));
		app.waitUntilDoneProcessing();

		ShowImages.showWindow(app,"Square Grid Detector",true);
	}
}
