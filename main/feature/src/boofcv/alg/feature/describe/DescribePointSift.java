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

package boofcv.alg.feature.describe;

import boofcv.alg.descriptor.UtilFeature;
import boofcv.alg.filter.kernel.KernelMath;
import boofcv.core.image.FactoryGImageSingleBand;
import boofcv.core.image.GImageSingleBand;
import boofcv.factory.filter.kernel.FactoryKernelGaussian;
import boofcv.struct.convolve.Kernel2D_F32;
import boofcv.struct.feature.TupleDesc_F64;
import boofcv.struct.image.ImageSingleBand;
import georegression.metric.UtilAngle;

/**
 * <p>A faithful implementation of the SIFT descriptor.</p>
 * <p>The descriptor is computed inside of a square grid which is scaled and rotated.  Each grid cell is composed
 * of a square sub-region.  If the sub-region is 4x4 and the outer grid is 5x5 then a total area of size 20x20
 * is sampled.  For each sub-region a histogram with N bins of orientations is computed. Orientation from each
 * sample point comes from the image's spacial derivative.  If the outer grid is 4x4 and the histogram N=8, then
 * the total descriptor will be 128 elements.</p>
 *
 * <p>When a point is sample, its orientation (-pi to pi) and magnitude sqrt(dx**2 + dy**2) are both computed.  A
 * contribution from this sample point is added to the entire descriptor and weighted using trilinear interpolation
 * (outer grid x-y coordinate, and orientation bin), Gaussian distribution centered at key point location, and the
 * magnitude.</p>
 *
 * <p>There are no intentional differences from the paper. However the paper is ambiguous in some places.</p>
 * <ul>
 *     <li>Interpolation method for sampling image pixels isn't specified.  Nearest-neighbor is assumed and that's what
 *     VLFeat uses too.</li>
 *     <li>Size of sample region.  Oddly enough, I can't find this very important parameter specified anywhere.
 *     The suggested value comes from empirical testing.</li>
 * </ul>
 *
 * <p>
 * [1] Lowe, D. "Distinctive image features from scale-invariant keypoints".
 * International Journal of Computer Vision, 60, 2 (2004), pp.91--110.
 * </p>
 *
 * @author Peter Abeles
 */
public class DescribePointSift<Deriv extends ImageSingleBand> {

	// spacial derivatives of input image
	GImageSingleBand imageDerivX, imageDerivY;

	// width of a subregion, in samples
	int widthSubregion;
	// width of the outer grid, in sub-regions
	int widthGrid;

	// number of bins in the orientation histogram
	int numHistogramBins;
	double histogramBinWidth;

	// conversion from scale-space sigma to image pixels
	double sigmaToPixels;

	// maximum value of an element in the descriptor
	double maxDescriptorElementValue;

	// precomputed gaussian weighting function
	float gaussianWeight[];

	// reference to user provided descriptor in which results are saved to
	TupleDesc_F64 descriptor;

	/**
	 * Configures the descriptor.
	 *
	 * @param widthSubregion Width of sub-region in samples.  Try 4
	 * @param widthGrid Width of grid in subregions.  Try 4.
	 * @param numHistogramBins Number of bins in histogram.  Try 8
	 * @param sigmaToPixels Conversion of sigma to pixels.  Used to scale the descriptor region.  Try 1.5 ??????
	 * @param weightingSigmaFraction Sigma for Gaussian weighting function is set to this value * region width.  Try 0.5
	 * @param maxDescriptorElementValue Helps with non-affine changes in lighting. See paper.  Try 0.2
	 */
	public DescribePointSift(int widthSubregion, int widthGrid, int numHistogramBins,
							 double sigmaToPixels, double weightingSigmaFraction,
							 double maxDescriptorElementValue , Class<Deriv> derivType ) {
		this.widthSubregion = widthSubregion;
		this.widthGrid = widthGrid;
		this.numHistogramBins = numHistogramBins;
		this.sigmaToPixels = sigmaToPixels;
		this.maxDescriptorElementValue = maxDescriptorElementValue;

		this.histogramBinWidth = 2.0*Math.PI/numHistogramBins;

		// number of samples wide the descriptor window is
		int descriptorWindow = widthSubregion*widthGrid;
		double weightSigma = descriptorWindow*weightingSigmaFraction;
		gaussianWeight = createGaussianWeightKernel(weightSigma,descriptorWindow/2);

		imageDerivX = FactoryGImageSingleBand.create(derivType);
		imageDerivY = FactoryGImageSingleBand.create(derivType);
	}

	/**
	 * Creates a gaussian weighting kernel with an even number of elements along its width
	 */
	private static float[] createGaussianWeightKernel( double sigma , int radius ) {
		Kernel2D_F32 ker = FactoryKernelGaussian.gaussian2D_F32(sigma,radius,false,false);
		float maxValue = KernelMath.maxAbs(ker.data,4*radius*radius);
		KernelMath.divide(ker,maxValue);
		return ker.data;
	}

	/**
	 * Sets the image spacial derivatives.  These should be computed from an image at the appropriate scale
	 * in scale-space.
	 *
	 * @param derivX x-derivative of input image
	 * @param derivY y-derivative of input image
	 */
	public void setImageGradient(Deriv derivX , Deriv derivY ) {
		this.imageDerivX.wrap(derivX);
		this.imageDerivY.wrap(derivY);
	}

	/**
	 * Computes the SIFT descriptor for the specified key point
	 *
	 * @param c_x center of key point.  x-axis
	 * @param c_y center of key point.  y-axis
	 * @param sigma Computed sigma in scale-space for this point
	 * @param orientation Orientation of keypoint in radians
	 * @param descriptor (output) Storage for computed descriptor.  Make sure it's the appropriate length first
	 */
	public void process( double c_x , double c_y , double sigma , double orientation , TupleDesc_F64 descriptor )
	{
		this.descriptor = descriptor;
		descriptor.fill(0);

		computeRawDescriptor(c_x, c_y, sigma, orientation);

		massageDescriptor();
	}


	/**
	 * Adjusts the descriptor.  This adds lighting invariance and reduces the affects of none-affine changes
	 * in lighting.
	 */
	void massageDescriptor() {
		// normalize descriptor to unit length
		UtilFeature.normalizeL2(descriptor);

		// clip the values
		for (int i = 0; i < descriptor.size(); i++) {
			double value = descriptor.value[i];
			if( value > maxDescriptorElementValue ) {
				descriptor.value[i] = maxDescriptorElementValue;
			}
		}

		// normalize again
		UtilFeature.normalizeL2(descriptor);
	}


	/**
	 * Computes the descriptor by sampling the input image.  This is raw because the descriptor hasn't been massaged
	 * yet.
	 */
	void computeRawDescriptor(double c_x, double c_y, double sigma, double orientation) {
		double c = Math.cos(orientation);
		double s = Math.sin(orientation);

		int sampleWidth = widthGrid*widthSubregion;
		// compute radius and ensure its symmetric for even and odd cases
		double sampleRadius = sampleWidth/2-(1-(sampleWidth%2))/2.0;

		double sampleToPixels = sigma*sigmaToPixels;

		Deriv image = (Deriv)imageDerivX.getImage();

		for (int sampleY = 0; sampleY < sampleWidth; sampleY++) {
			float subY = sampleY/widthSubregion;
			double y = sampleToPixels*(sampleY-sampleRadius);

			for (int sampleX = 0; sampleX < sampleWidth; sampleX++) {
				// coordinate of samples in terms of sub-region.  Center of sample point, hence + 0.5f
				float subX = sampleX/widthSubregion;
				// recentered local pixel sample coordinate
				double x = sampleToPixels*(sampleX-sampleRadius);

				// pixel coordinate in the image that is to be sampled.  Note the rounding
				// If the pixel coordinate is -1 < x < 0 then it will round to 0 instead of -1, but the rounding
				// method below is WAY faster than Math.round() so this is a small loss.
				int pixelX = (int)(x*c - y*s + c_x + 0.5);
				int pixelY = (int)(x*s + y*c + c_y + 0.5);

				// skip pixels outside of the image
				if( image.isInBounds(pixelX,pixelY) ) {
					// spacial image derivative at this point
					float spacialDX = imageDerivX.unsafe_getF(pixelX, pixelY);
					float spacialDY = imageDerivY.unsafe_getF(pixelX, pixelY);

					double adjDX =  c*spacialDX + s*spacialDY;
					double adjDY = -s*spacialDX + c*spacialDY;

					double angle = UtilAngle.domain2PI(Math.atan2(adjDY,adjDX));

					float weightGaussian = gaussianWeight[sampleY*sampleWidth+sampleX];
					float weightGradient = (float)Math.sqrt(spacialDX*spacialDX + spacialDY*spacialDY);

					// trilinear interpolation intro descriptor
					trilinearInterpolation(weightGaussian*weightGradient,subX,subY,angle);
				}
			}
		}
	}

	/**
	 * Applies trilinear interpolation across the descriptor
	 */
	void trilinearInterpolation( float weight , float sampleX , float sampleY , double angle )
	{
		for (int i = 0; i < widthGrid; i++) {
			double weightGridY = 1.0 - Math.abs(sampleY-i);
			if( weightGridY <= 0) continue;
			for (int j = 0; j < widthGrid; j++) {
				double weightGridX = 1.0 - Math.abs(sampleX-j);
				if( weightGridX <= 0 ) continue;
				for (int k = 0; k < numHistogramBins; k++) {
					double angleBin = k*histogramBinWidth;
					double weightHistogram = 1.0 - UtilAngle.dist(angle,angleBin)/histogramBinWidth;
					if( weightHistogram <= 0 ) continue;

					int descriptorIndex = (i*widthGrid + j)*numHistogramBins + k;
					descriptor.value[descriptorIndex] += weight*weightGridX*weightGridY*weightHistogram;
				}
			}
		}
	}

	/**
	 * Number of elements in the descriptor.
	 */
	public int getDescriptorLength() {
		return widthGrid*widthGrid*numHistogramBins;
	}

	/**
	 * Radius of descriptor in pixels.  Width is radius*2
	 */
	public int getCanonicalRadius() {
		return widthGrid*widthSubregion/2;
	}
}
