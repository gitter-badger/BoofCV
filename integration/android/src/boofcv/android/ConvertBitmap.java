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

package boofcv.android;

import android.graphics.Bitmap;
import boofcv.struct.image.*;

import java.nio.ByteBuffer;

/**
 * Functions for converting Android Bitmap images into BoofCV formats.  In earlier versions of Android
 * there is no way to directly access the internal array used by Bitmap.  You have to provide an array for it to
 * be copied into.  This is why the storage array is provided.
 * 
 * @author Peter Abeles
 *
 */
public class ConvertBitmap {
	
	/**
	 * Declares a storage array for the provided Bitmap image.  The array will be
	 * of size = width*height*byteCount where byteCount is the number of bytes per
	 * pixel.
	 * 
	 * @param input Bitmap image.
	 * @param storage If not null then the array size will be checked, if too small a new array will be returned.
	 * @return An array of appropriate size.
	 */
	public static byte[] declareStorage( Bitmap input , byte[] storage ) {
		int byteCount = input.getConfig() == Bitmap.Config.ARGB_8888 ? 4 : 2;
		int length = input.getWidth()*input.getHeight()*byteCount;
		
		if( storage == null || storage.length < length )
			return new byte[ length ];
		return storage;
	}

	/**
	 * Converts a {@link Bitmap} into a BoofCV image.  Type is determined at runtime.
	 * @param input Bitmap image.
	 * @param output Output image
	 * @param storage Byte array used for internal storage. If null it will be declared internally.
	 */
	public static <T extends ImageBase>
	void bitmapToBoof( Bitmap input , T output , byte[] storage) {
		switch( output.getImageType().getFamily() ) {
			case SINGLE_BAND: {
				if( output.getClass() == ImageFloat32.class )
					bitmapToGray(input, (ImageFloat32) output, storage);
				else if( output.getClass() == ImageUInt8.class )
					bitmapToGray(input,(ImageUInt8)output,storage);
				else
					throw new IllegalArgumentException("Unsupported BoofCV Image Type");
			} break;

			case MULTI_SPECTRAL:
				MultiSpectral ms = (MultiSpectral)output;
				bitmapToMS(input,ms,ms.getBandType(),storage);
			break;

			default:
				throw new IllegalArgumentException("Unsupported BoofCV Image Type");
		}
	}

	/**
	 * Converts Bitmap image into a single band image of arbitrary type.
	 *
	 * @see #declareStorage(android.graphics.Bitmap, byte[])
	 * 
	 * @param input Input Bitmap image.
	 * @param output Output single band image.  If null a new one will be declared.
	 * @param imageType Type of single band image.
	 * @param storage Byte array used for internal storage. If null it will be declared internally. 
	 * @return The converted gray scale image.
	 */
	public static <T extends ImageSingleBand> 
	T bitmapToGray( Bitmap input , T output , Class<T> imageType , byte[] storage) {
		if( imageType == ImageFloat32.class )
			return (T)bitmapToGray(input,(ImageFloat32)output,storage);
		else if( imageType == ImageUInt8.class )
			return (T)bitmapToGray(input,(ImageUInt8)output,storage);
		else
			throw new IllegalArgumentException("Unsupported BoofCV Image Type");
		
	}
	
	/**
	 * Converts Bitmap image into ImageUInt8.
	 * 
	 * @param input Input Bitmap image.
	 * @param output Output image.  If null a new one will be declared.
	 * @param storage Byte array used for internal storage. If null it will be declared internally. 
	 * @return The converted gray scale image.
	 */
	public static ImageUInt8 bitmapToGray( Bitmap input , ImageUInt8 output , byte[] storage ) {
		if( output == null ) {
			output = new ImageUInt8( input.getWidth() , input.getHeight() );
		} else if( output.getWidth() != input.getWidth() || output.getHeight() != input.getHeight() ) {
			throw new IllegalArgumentException("Image shapes are not the same");
		}
		
		if( storage == null )
			storage = declareStorage(input,null);
		input.copyPixelsToBuffer(ByteBuffer.wrap(storage));
		
		ImplConvertBitmap.arrayToGray(storage, input.getConfig(), output);
		
		return output;
	}
	
	/**
	 * Converts Bitmap image into ImageFloat32.
	 *
	 * @see #declareStorage(android.graphics.Bitmap, byte[])
	 * 
	 * @param input Input Bitmap image.
	 * @param output Output image.  If null a new one will be declared.
	 * @param storage Byte array used for internal storage. If null it will be declared internally.
	 * @return The converted gray scale image.
	 */
	public static ImageFloat32 bitmapToGray( Bitmap input , ImageFloat32 output , byte[] storage) {
		if( output == null ) {
			output = new ImageFloat32( input.getWidth() , input.getHeight() );
		} else if( output.getWidth() != input.getWidth() || output.getHeight() != input.getHeight() ) {
			throw new IllegalArgumentException("Image shapes are not the same");
		}
		
		if( storage == null )
			storage = declareStorage(input,null);
		input.copyPixelsToBuffer(ByteBuffer.wrap(storage));
		
		ImplConvertBitmap.arrayToGray(storage, input.getConfig(), output);
		
		return output;
	}
	
	/**
	 * Converts Bitmap image into MultiSpectral image of the appropriate type.
	 *
	 * @see #declareStorage(android.graphics.Bitmap, byte[])
	 * 
	 * @param input Input Bitmap image.
	 * @param output Output image.  If null a new one will be declared.
	 * @param type The type of internal single band image used in the MultiSpectral image.
	 * @param storage Byte array used for internal storage. If null it will be declared internally.
	 * @return The converted MultiSpectral image.
	 */
	public static <T extends ImageSingleBand>
	MultiSpectral<T> bitmapToMS( Bitmap input , MultiSpectral<T> output , Class<T> type , byte[] storage ) {
		if( output == null ) {
			output = new MultiSpectral<T>( type , input.getWidth() , input.getHeight() , 4 );
		} else if( output.getWidth() != input.getWidth() || output.getHeight() != input.getHeight() ) {
			throw new IllegalArgumentException("Image shapes are not the same");
		}

		if( storage == null )
			storage = declareStorage(input,null);
		input.copyPixelsToBuffer(ByteBuffer.wrap(storage));
		
		if( type == ImageUInt8.class )
			ImplConvertBitmap.arrayToMulti_U8(storage, input.getConfig(), (MultiSpectral)output);
		else if( type == ImageFloat32.class )
			ImplConvertBitmap.arrayToMulti_F32(storage, input.getConfig(), (MultiSpectral)output);
		else
			throw new IllegalArgumentException("Unsupported BoofCV Type");

		return output;
	}

	/**
	 * Converts many BoofCV image types into a Bitmap.
	 *
	 * @see #declareStorage(android.graphics.Bitmap, byte[])
	 *
	 * @param input Input BoofCV image.
	 * @param output Output Bitmap image.
	 * @param storage Byte array used for internal storage. If null it will be declared internally.
	 */
	public static void boofToBitmap( ImageBase input , Bitmap output , byte[] storage) {
		if( input instanceof MultiSpectral ) {
			multiToBitmap((MultiSpectral)input,output,storage);
		} else if( input instanceof ImageSingleBand ) {
			grayToBitmap((ImageSingleBand)input,output,storage);
		} else if( input instanceof ImageInterleaved ) {
			interleavedToBitmap((ImageInterleaved) input, output, storage);
		} else {
			throw new IllegalArgumentException("Unsupported input image type");
		}
	}

	/**
	 * Converts ImageSingleBand into Bitmap.
	 *
	 * @see #declareStorage(android.graphics.Bitmap, byte[])
	 * 
	 * @param input Input gray scale image.
	 * @param output Output Bitmap image.
	 * @param storage Byte array used for internal storage. If null it will be declared internally.
	 */
	public static void grayToBitmap( ImageSingleBand input , Bitmap output , byte[] storage) {
		if( input instanceof ImageUInt8 )
			grayToBitmap((ImageUInt8)input,output,storage);
		else if( input instanceof ImageFloat32 )
			grayToBitmap((ImageFloat32)input,output,storage);
		else
			throw new IllegalArgumentException("Unsupported BoofCV Type: "+input);
	}
	
	/**
	 * Converts ImageSingleBand into Bitmap.
	 *
	 * @see #declareStorage(android.graphics.Bitmap, byte[])
	 * 
	 * @param input Input gray scale image.
	 * @param output Output Bitmap image.
	 * @param storage Byte array used for internal storage. If null it will be declared internally.
	 */
	public static void grayToBitmap( ImageUInt8 input , Bitmap output , byte[] storage) {
		if( output.getWidth() != input.getWidth() || output.getHeight() != input.getHeight() ) {
			throw new IllegalArgumentException("Image shapes are not the same");
		}
		
		if( storage == null )
			storage = declareStorage(output,null);
		
		ImplConvertBitmap.grayToArray(input, storage,output.getConfig());
		output.copyPixelsFromBuffer(ByteBuffer.wrap(storage));
	}
	
	/**
	 * Converts ImageSingleBand into Bitmap.
	 *
	 * @see #declareStorage(android.graphics.Bitmap, byte[])
	 * 
	 * @param input Input gray scale image.
	 * @param output Output Bitmap image.
	 * @param storage Byte array used for internal storage. If null it will be declared internally.
	 */
	public static void grayToBitmap( ImageFloat32 input , Bitmap output , byte[] storage ) {
		if( output.getWidth() != input.getWidth() || output.getHeight() != input.getHeight() ) {
			throw new IllegalArgumentException("Image shapes are not the same");
		}
		
		if( storage == null )
			storage = declareStorage(output,null);
		
		ImplConvertBitmap.grayToArray(input, storage,output.getConfig());
		output.copyPixelsFromBuffer(ByteBuffer.wrap(storage));
	}
	
	/**
	 * Converts MultiSpectral image into Bitmap.
	 *
	 * @see #declareStorage(android.graphics.Bitmap, byte[])
	 *
	 * @param input Input MultiSpectral image.
	 * @param output Output Bitmap image.
	 * @param storage Byte array used for internal storage. If null it will be declared internally.
	 */
	public static <T extends ImageSingleBand>
	void multiToBitmap(  MultiSpectral<T> input , Bitmap output , byte[] storage ) {
		if( output.getWidth() != input.getWidth() || output.getHeight() != input.getHeight() ) {
			throw new IllegalArgumentException("Image shapes are not the same");
		}
		
		if( storage == null )
			storage = declareStorage(output,null);
		
		if( input.getBandType() == ImageUInt8.class )
			ImplConvertBitmap.multiToArray_U8((MultiSpectral)input, storage,output.getConfig());
		else if( input.getBandType() == ImageFloat32.class )
			ImplConvertBitmap.multiToArray_F32((MultiSpectral)input, storage,output.getConfig());
		else
			throw new IllegalArgumentException("Unsupported BoofCV Type");
		output.copyPixelsFromBuffer(ByteBuffer.wrap(storage));
	}

	/**
	 * Converts {@link ImageInterleaved} image into Bitmap.
	 *
	 * @see #declareStorage(android.graphics.Bitmap, byte[])
	 *
	 * @param input Input MultiSpectral image.
	 * @param output Output Bitmap image.
	 * @param storage Byte array used for internal storage. If null it will be declared internally.
	 */
	public static <T extends ImageInterleaved>
	void interleavedToBitmap(T input, Bitmap output, byte[] storage) {
		if( output.getWidth() != input.getWidth() || output.getHeight() != input.getHeight() ) {
			throw new IllegalArgumentException("Image shapes are not the same");
		}

		if( storage == null )
			storage = declareStorage(output,null);

		if( input.getImageType().getDataType() == ImageDataType.U8 )
			ImplConvertBitmap.interleavedToArray((InterleavedU8) input, storage, output.getConfig());
		else if( input.getImageType().getDataType() == ImageDataType.F32 )
			ImplConvertBitmap.interleavedToArray((InterleavedF32) input, storage, output.getConfig());
		else
			throw new IllegalArgumentException("Unsupported BoofCV Type");
		output.copyPixelsFromBuffer(ByteBuffer.wrap(storage));
	}
	
	/**
	 * Converts ImageUInt8 into a new Bitmap.
	 * 
	 * @param input Input gray scale image.
	 * @param config Type of Bitmap image to create.
	 */
	public static Bitmap grayToBitmap( ImageUInt8 input , Bitmap.Config config ) {
		Bitmap output = Bitmap.createBitmap(input.width, input.height, config);
		
		grayToBitmap(input,output,null);
		
		return output;
	}
	
	/**
	 * Converts ImageFloat32 into a new Bitmap.
	 * 
	 * @param input Input gray scale image.
	 * @param config Type of Bitmap image to create.
	 */
	public static Bitmap grayToBitmap( ImageFloat32 input , Bitmap.Config config ) {
		Bitmap output = Bitmap.createBitmap(input.width, input.height, config);
		
		grayToBitmap(input,output,null);
		
		return output;
	}
}
