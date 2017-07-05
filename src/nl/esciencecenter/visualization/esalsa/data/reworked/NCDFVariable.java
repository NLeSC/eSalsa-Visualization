package nl.esciencecenter.visualization.esalsa.data.reworked;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;

import javax.media.opengl.GL3;

import nl.esciencecenter.neon.math.Float2Matrix;
import nl.esciencecenter.neon.math.Float2Vector;
import nl.esciencecenter.neon.swing.ColormapInterpreter.Dimensions;
import nl.esciencecenter.visualization.esalsa.CacheFileManager;
import nl.esciencecenter.visualization.esalsa.ImauSettings;
import nl.esciencecenter.visualization.esalsa.IntArrayTexture;
import nl.esciencecenter.visualization.esalsa.JOCLColormapper;
import nl.esciencecenter.visualization.esalsa.Texture2D;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;
import ucar.unidata.geoloc.Projection;
import ucar.unidata.geoloc.ProjectionPoint;

public class NCDFVariable {
	private final static Logger logger = LoggerFactory
			.getLogger(NCDFVariable.class);
	private final static int MAX_TIMESTEPS_IN_SINGLE_FILE = 10000;
	private final ImauSettings settings = ImauSettings.getInstance();

	private final static String[] timeStrings = { "time", "TIME" };
	private final static String[] heightStrings = { "z_t", "lev", "depth", "Z_T", "LEV", "DEPTH" };
	private final static String[] latStrings = { "lat", "LAT", "nj", "NJ", "j" };
	private final static String[] lonStrings = { "lon", "LON", "ni", "NI", "i" };

	private class TimeStep implements Comparable<TimeStep> {
		File file;
		int timeStepWithinFile;
		double timeInMetadata;

		public TimeStep(File file, int timeStepWithinFile, double timeInMetadata) {
			this.file = file;
			this.timeStepWithinFile = timeStepWithinFile;
			this.timeInMetadata = timeInMetadata;
		}

		public File getFile() {
			return file;
		}

		public int getTimeStepWithinFile() {
			return timeStepWithinFile;
		}

		public double getTimeInMetadata() {
			return timeInMetadata;
		}

		@Override
		public int compareTo(TimeStep other) {
			if (timeInMetadata < other.getTimeInMetadata()) {
				return -1;
			} else if (timeInMetadata > other.getTimeInMetadata()) {
				return 1;
			}

			return 0;
		}
	}

	private final Variable variable;
	private final List<TimeStep> timeSteps;

	private int heightDimensionSize = 0;
	private int latDimensionSize = 0;
	private int lonDimensionSize = 0;

	private float[] realLatitudeValues;
	private float[] realLongitudeValues;

	private float minimumValue, maximumValue, fillValue, minimumLatitude,
			maximumLatitude;
	
	private List<Float2Vector> tCoords = null;

	private final CacheFileManager cacheAtDataLocation;
	private IntArrayTexture latitudeTex;
	private IntArrayTexture longitudeTex;

	// private final CacheFileManager cacheAtProgramLocation;

	public NCDFVariable(Variable variable, List<File> filesToBeAnalysed)
			throws VariableNotCompatibleException, IOException {
		cacheAtDataLocation = settings.getCacheFileManagerAtDataLocation();
		// cacheAtProgramLocation =
		// settings.getCacheFileManagerAtProgramLocation();

		timeSteps = new ArrayList<TimeStep>();
		this.variable = variable;

		// Loop over the files to see if the variable is in there
		for (File file : filesToBeAnalysed) {
			NetcdfFile ncfile = NetcdfFile.open(file.getAbsolutePath());

			Variable variableInThisFile = ncfile.findVariable(variable
					.getFullName());
			if (variableInThisFile != null) {
				// seems to be in there, lets analyse the dimensions and see if
				// they match our previously found dimensions

				int currentHeightDimensionSize = getHeightDimensionSize(ncfile, variable);
				if (heightDimensionSize != currentHeightDimensionSize) {
					if (heightDimensionSize == 0) {
						heightDimensionSize = currentHeightDimensionSize;
					} else {
						throw new VariableNotCompatibleException("Variable "
								+ variable.getFullName()
								+ " was found with mismatching dimensions");
					}
				}

				int currentlatDimensionSize = getLatitudeDimensionSize(ncfile, variable);
				if (latDimensionSize != currentlatDimensionSize) {
					if (latDimensionSize == 0) {
						latDimensionSize = currentlatDimensionSize;
					} else {
						throw new VariableNotCompatibleException("Variable "
								+ variable.getFullName()
								+ " was found with mismatching dimensions");
					}
				}

				int currentlonDimensionSize = getLongitudeDimensionSize(ncfile, variable);
				if (lonDimensionSize != currentlonDimensionSize) {
					if (lonDimensionSize == 0) {
						lonDimensionSize = currentlonDimensionSize;
					} else {
						throw new VariableNotCompatibleException("Variable "
								+ variable.getFullName()
								+ " was found with mismatching dimensions");
					}
				}

				if (latDimensionSize > 0 && lonDimensionSize > 0) {
					// Since we didnt get an exception yet, it seems it's
					// mappable, so we need to do our thing
					determineLatBounds(ncfile, variableInThisFile);

					// Loop over the dimensions to see if there's a time
					// dimension
					Variable timeVar = getTimeVariable(ncfile,
							variableInThisFile);
					Array timeArray = timeVar.read();
					
					for (int t = 0; t < timeArray.getSize(); t++) {
						double timeInFile = timeArray.getDouble(t);
						if (variable.getFullName().compareTo("PREC") == 0
								|| variable.getFullName().compareTo("PRECC") == 0
								|| variable.getFullName().compareTo("PRECL") == 0
								|| variable.getFullName().compareTo("V") == 0
								|| variable.getFullName().compareTo("U") == 0) {
							timeInFile += 365.0;
						}
						TimeStep newTimeStep = new TimeStep(file, t, timeInFile);
						timeSteps.add(newTimeStep);
					}
					
//					if (timeSteps.size() == 1) {
//						setTextureCoordinates(ncfile, variableInThisFile);
//						calcTextureCoordinates(ncfile, variableInThisFile);	
//					}
				}
			}
			ncfile.close();
		}
		
//		System.out.println("Variable "+ variable.getFullName() + " added with "+timeSteps.size() + " timesteps.");

		Collections.sort(timeSteps);

		try {
			analyseBounds();
		} catch (NoSuchSequenceNumberException | InvalidRangeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void setTextureCoordinates(NetcdfFile ncfile, Variable variableInFile) throws IOException {	
		Variable latitudes = getLatitudeVariable(ncfile, variableInFile);
		Variable longitudes = getLongitudeVariable(ncfile, variableInFile);

		float[] realLatitudeValues = null, realLongitudeValues = null;
		if (latitudes != null) {
			Array netCDFArray = latitudes.read();
			if (latitudes.getDataType() == DataType.FLOAT) {
				realLatitudeValues = (float[]) netCDFArray.get1DJavaArray(float.class);
			} else if (latitudes.getDataType() == DataType.DOUBLE) {
				double[] temp = (double[]) netCDFArray.get1DJavaArray(double.class);
				realLatitudeValues = new float[temp.length];
				for (int i = 0; i < temp.length; i++) {
					realLatitudeValues[i] = (float) temp[i];
				}
			}
		}
		
		JOCLColormapper mapper = new JOCLColormapper();
		int[] pixelArray = mapper.makeImage("dry_wet", new Dimensions(-90f,90f), realLatitudeValues, -1f,
	            false, latitudes.getShape()[0], latitudes.getShape()[1]);

        latitudeTex = new IntArrayTexture(GL3.GL_TEXTURE10, pixelArray, latitudes.getShape()[0], latitudes.getShape()[1]);

		if (longitudes != null) {
			Array netCDFArray = longitudes.read();
			if (latitudes.getDataType() == DataType.FLOAT) {
				realLongitudeValues = (float[]) netCDFArray.get1DJavaArray(float.class);
			} else if (latitudes.getDataType() == DataType.DOUBLE) {
				double[] temp = (double[]) netCDFArray.get1DJavaArray(double.class);
				realLongitudeValues = new float[temp.length];
				for (int i = 0; i < temp.length; i++) {
					realLongitudeValues[i] = (float) temp[i];
				}
			}
		}
		
		int[] pixelArrayLon = mapper.makeImage("dry_wet", new Dimensions(0f,360f), realLongitudeValues, -1f,
	            false, longitudes.getShape()[0], longitudes.getShape()[1]);

        longitudeTex = new IntArrayTexture(GL3.GL_TEXTURE11, pixelArrayLon, longitudes.getShape()[0], longitudes.getShape()[1]);
        
//		List<Float2Vector> tCoords = new ArrayList<Float2Vector>();
//		if (realLatitudeValues.length == realLongitudeValues.length) {	
//			int length = realLatitudeValues.length;
//			
//			for (int i=0; i < length; i++) {
//				tCoords.add(new Float2Vector(realLongitudeValues[i], realLatitudeValues[i]));
//			}
//		}	
//		this.tCoords = tCoords;
	}
	
	private void calcTextureCoordinates(NetcdfFile ncfile, Variable variableInFile) throws IOException {		
		Variable latitudes = getLatitudeVariable(ncfile, variableInFile);
		Variable longitudes = getLongitudeVariable(ncfile, variableInFile);

		float[] realLatitudeValues = null, realLongitudeValues = null;
		if (latitudes != null) {
			Array netCDFArray = latitudes.read();
			if (latitudes.getDataType() == DataType.FLOAT) {
				realLatitudeValues = (float[]) netCDFArray.get1DJavaArray(float.class);
			} else if (latitudes.getDataType() == DataType.DOUBLE) {
				double[] temp = (double[]) netCDFArray.get1DJavaArray(double.class);
				realLatitudeValues = new float[temp.length];
				for (int i = 0; i < temp.length; i++) {
					realLatitudeValues[i] = (float) temp[i];
				}
			}
		}

		if (longitudes != null) {
			Array netCDFArray = longitudes.read();
			if (latitudes.getDataType() == DataType.FLOAT) {
				realLongitudeValues = (float[]) netCDFArray.get1DJavaArray(float.class);
			} else if (latitudes.getDataType() == DataType.DOUBLE) {
				double[] temp = (double[]) netCDFArray.get1DJavaArray(double.class);
				realLongitudeValues = new float[temp.length];
				for (int i = 0; i < temp.length; i++) {
					realLongitudeValues[i] = (float) temp[i];
				}
			}
		}
		
		Float2Vector[] tCoords = null;
		if (realLatitudeValues.length == realLongitudeValues.length) {	
			int length = realLatitudeValues.length;
			
			tCoords = new Float2Vector[length];
		
			float[] tCoord_S = new float[length];
			float[] tCoord_T = new float[length];
			
			if (realLatitudeValues != null && realLongitudeValues != null) {				
				for (int i=0; i < length; i++) {
					tCoord_T[i] = realLatitudeValues[i] + 90f / 1f;
				}				
			
				for (int i=0; i < length; i++) {
					tCoord_S[i] = realLongitudeValues[i] / 1f;
				}
			}
			
			for (int i=0; i < length; i++) {
				tCoords[i] = new Float2Vector(tCoord_S[i], tCoord_T[i]);
			}
		}	
//		this.tCoords = tCoords;
	}

	private void determineLatBounds(NetcdfFile ncfile, Variable variableInFile)
			throws IOException {
		float latMin = cacheAtDataLocation.readLatMin(variable.getFullName());
		float latMax = cacheAtDataLocation.readLatMax(variable.getFullName());
		// if (Float.isNaN(latMin) || Float.isNaN(latMax)) {
		// latMin = cacheAtProgramLocation.readLatMin(variable.getFullName());
		// latMax = cacheAtProgramLocation.readLatMax(variable.getFullName());
		// }
		if (Float.isNaN(latMin) || Float.isNaN(latMax)) {
			Variable latitudes = getLatitudeVariable(ncfile, variableInFile);
			if (latitudes != null) {
				float fillValue = Float.NEGATIVE_INFINITY;
				for (Attribute a : latitudes.getAttributes()) {
					if (a.getFullName().compareTo("_FillValue") == 0) {
						fillValue = a.getNumericValue().floatValue();
					}
				}

				Array netCDFArray = latitudes.read();
				realLatitudeValues = (float[]) netCDFArray
						.get1DJavaArray(float.class);

				float min = Float.POSITIVE_INFINITY;
				float max = Float.NEGATIVE_INFINITY;
				for (float value : realLatitudeValues) {
					if (value != fillValue) {
						if (value < min) {
							min = value;
						}
						if (value > max) {
							max = value;
						}
					}
				}
				minimumLatitude = min;
				maximumLatitude = max;

				 cacheAtDataLocation.writeLatMin(variableInFile.getFullName(),
				 min);
				 cacheAtDataLocation.writeLatMax(variableInFile.getFullName(),
				 max);

				// cacheAtProgramLocation.writeLatMin(variableInFile.getFullName(),
				// min);
				// cacheAtProgramLocation.writeLatMax(variableInFile.getFullName(),
				// max);
			} else {
				minimumLatitude = -90f;
				maximumLatitude = 90f;
			}
		} else {
			maximumLatitude = latMax;
			minimumLatitude = latMin;
		}

		logger.debug("latitudes for " + variable.getFullName()
				+ " exist between " + minimumLatitude + " and "
				+ maximumLatitude);
	}

	private void analyseBounds() throws NoSuchSequenceNumberException,
			InvalidRangeException, IOException {
		// First, determine the fillValue, we dont want that skewing our
		// results...
		fillValue = Float.NEGATIVE_INFINITY;
		for (Attribute a : variable.getAttributes()) {
			if (a.getFullName().compareTo("_FillValue") == 0) {
				fillValue = a.getNumericValue().floatValue();
			}
		}

		float resultMin = Float.NaN, resultMax = Float.NaN;

		// Check the settings first to see if this value was predefined.
		float settingsMin = settings.getVarMin(variable.getFullName());
		float settingsMax = settings.getVarMax(variable.getFullName());
		if (!Float.isNaN(settingsMin)) {
			resultMin = settingsMin;
			logger.debug("Settings hit for min " + variable.getFullName()
					+ " : " + resultMin);
		}
		if (!Float.isNaN(settingsMax)) {
			resultMax = settingsMax;
			logger.debug("Settings hit for max " + variable.getFullName()
					+ " : " + resultMax);
		}

		// Then Check if we have made a cacheFileManager file earlier and the
		// value is in there
		if (Float.isNaN(resultMin)) {
			float cacheMin = cacheAtDataLocation
					.readMin(variable.getFullName());
			if (!Float.isNaN(cacheMin)) {
				resultMin = cacheMin;
				logger.debug("Cache hit for min " + variable.getFullName()
						+ " : " + resultMin);
			}
		}

		if (Float.isNaN(resultMax)) {
			float cacheMax = cacheAtDataLocation
					.readMax(variable.getFullName());
			if (!Float.isNaN(cacheMax)) {
				resultMax = cacheMax;
				logger.debug("Cache hit for max " + variable.getFullName()
						+ " : " + resultMax);
			}
		}

		// if (Float.isNaN(resultMin)) {
		// float cacheMin =
		// cacheAtProgramLocation.readMin(variable.getFullName());
		// if (!Float.isNaN(cacheMin)) {
		// resultMin = cacheMin;
		// logger.debug("Cache hit for min " + variable.getFullName() + " : " +
		// resultMin);
		// }
		// }
		//
		// if (Float.isNaN(resultMax)) {
		// float cacheMax =
		// cacheAtProgramLocation.readMax(variable.getFullName());
		// if (!Float.isNaN(cacheMax)) {
		// resultMax = cacheMax;
		// logger.debug("Cache hit for max " + variable.getFullName() + " : " +
		// resultMax);
		// }
		// }

		// If we have both covered by now, we're done and don't need to read the
		// file.
		if (!Float.isNaN(resultMin) && !Float.isNaN(resultMax)) {
			minimumValue = resultMin;
			maximumValue = resultMax;
		} else {
			// One of these is not in settings, not in cache, so we need to
			// determine the bounds by hand.
			float tempMin = Float.POSITIVE_INFINITY, tempMax = Float.NEGATIVE_INFINITY;
			for (double t : getTimes()) {
				if (heightDimensionSize == 0) {
					float[] dataSlice = getData(t, 0);
					for (int i = 0; i < dataSlice.length; i++) {
						float value = dataSlice[i];
						if (value != fillValue && value < tempMin) {
							tempMin = value;
						}
						if (value != fillValue && value > tempMax) {
							tempMax = value;
						}
					}
				} else if (heightDimensionSize == 1) {
					float[] dataSlice = getData(t, 0);
					for (int i = 0; i < dataSlice.length; i++) {
						float value = dataSlice[i];
						if (value != fillValue && value < tempMin) {
							tempMin = value;
						}
						if (value != fillValue && value > tempMax) {
							tempMax = value;
						}
					}
					if (tempMin == Float.POSITIVE_INFINITY
							&& tempMax == Float.NEGATIVE_INFINITY) {
						dataSlice = getData(t, 1);
						for (int i = 0; i < dataSlice.length; i++) {
							float value = dataSlice[i];
							if (value != fillValue && value < tempMin) {
								tempMin = value;
							}
							if (value != fillValue && value > tempMax) {
								tempMax = value;
							}
						}
					}
				} else {
					for (int d = 0; d < heightDimensionSize; d++) {
						float[] dataSlice = getData(t, d);
						for (int i = 0; i < dataSlice.length; i++) {
							float value = dataSlice[i];
							if (value != fillValue && value < tempMin) {
								tempMin = value;
							}
							if (value != fillValue && value > tempMax) {
								tempMax = value;
							}
						}
					}
				}
			}
			if (!Float.isNaN(resultMin)) {
				minimumValue = resultMin;
			} else {
				minimumValue = tempMin;
				logger.debug("Calculated min " + variable.getFullName() + " : "
						+ tempMin);

				 cacheAtDataLocation.writeMin(variable.getFullName(),
				 minimumValue);
				// cacheAtProgramLocation.writeMin(variable.getFullName(),
				// minimumValue);

			}
			if (!Float.isNaN(resultMax)) {
				maximumValue = resultMax;
			} else {
				maximumValue = tempMax;
				logger.debug("Calculated max " + variable.getFullName() + " : "
						+ tempMax);

				 cacheAtDataLocation.writeMax(variable.getFullName(),
				 maximumValue);
				// cacheAtProgramLocation.writeMax(variable.getFullName(),
				// maximumValue);
			}
		}

		settings.setVarMin(variable.getFullName(), minimumValue);
		settings.setVarMax(variable.getFullName(), maximumValue);
	}

	public synchronized Texture2D getLatTexMap() {
		return latitudeTex;
	}

	public synchronized Texture2D getLonTexMap() {
		return longitudeTex;
	}
	
	public synchronized float[] getData(double time, int requestedDepth)
			throws NoSuchSequenceNumberException, InvalidRangeException,
			IOException {
		File wantedFile = null;
		TimeStep wantedTimestep = null;
		for (TimeStep t : timeSteps) {
			if (t.getTimeInMetadata() == time) {
				wantedFile = t.getFile();
				wantedTimestep = t;
			}
		}
		if (wantedFile == null || wantedTimestep == null) {
			throw new NoSuchSequenceNumberException("Time " + time
					+ " requested but not available.");
		}

		NetcdfFile netcdfFile = NetcdfFile.open(wantedFile.getAbsolutePath());
		Variable fileVariable = netcdfFile.findVariable(variable.getFullName());

		Array netCDFArray = null;
		if (heightDimensionSize > 0) {
			netCDFArray = fileVariable
					.slice(0, wantedTimestep.getTimeStepWithinFile())
					.slice(0, requestedDepth).read();
		} else {
			netCDFArray = fileVariable.slice(0,
					wantedTimestep.getTimeStepWithinFile()).read();
		}

		float[] data = null;
		if (fileVariable.getDataType() == DataType.FLOAT) {
			data = (float[]) netCDFArray.get1DJavaArray(float.class);
		} else if (fileVariable.getDataType() == DataType.DOUBLE) {
			double[] dData = (double[]) netCDFArray.get1DJavaArray(double.class);
			data = new float[dData.length];
			for (int i = 0; i < dData.length; i++) {
				data[i] = (float) dData[i];
			}
		}

		netcdfFile.close();

		return data;
	}

	public synchronized int getHeightDimensionSize() {
		return heightDimensionSize;
	}

	public synchronized int getLatDimensionSize() {
		return latDimensionSize;
	}

	public synchronized int getLonDimensionSize() {
		return lonDimensionSize;
	}

	public synchronized List<Double> getTimes() {
		List<Double> result = new ArrayList<Double>();
		for (TimeStep t : timeSteps) {
			result.add(t.getTimeInMetadata());
		}
		return result;
	}

	public synchronized float getMinimumValue() {
		return minimumValue;
	}

	public synchronized float getMaximumValue() {
		return maximumValue;
	}

	public synchronized float getFillValue() {
		return fillValue;
	}

	public synchronized String getName() {
		return variable.getFullName();
	}

	public synchronized String getDescription() {
		return variable.getDescription();
	}

	public synchronized String getUnits() {
		return variable.getUnitsString();
	}

	public synchronized String getTime(double time) {
		TimeStep wantedTimestep = null;
		for (TimeStep t : timeSteps) {
			if (t.getTimeInMetadata() == time) {
				wantedTimestep = t;
			}
		}

		Calendar epoch = new GregorianCalendar(01, 01, 0000);
		int days = (int) wantedTimestep.getTimeInMetadata();
		epoch.add(Calendar.DAY_OF_MONTH, days);

		NumberFormat formatter = new DecimalFormat("0000");
		String yearString = formatter.format(epoch.get(Calendar.YEAR));
		formatter = new DecimalFormat("00");
		String monthString = formatter.format(epoch.get(Calendar.MONTH));
		String dayString = formatter.format(epoch.get(Calendar.DAY_OF_MONTH));
		return "" + dayString + "-" + monthString + "-" + yearString;
	}

	public float getMinLatitude() {
		return minimumLatitude;
	}

	public float getMaxLatitude() {
		return maximumLatitude;
	}

	public static boolean isCompatible(NetcdfFile ncfile, Variable var) {
		boolean hasTime = false;
		boolean hasLat = false;
		boolean hasLon = false;

		if (getTimeDimensionSize(ncfile, var) > 0) {
			hasTime = true;
		}
		if (getLatitudeDimensionSize(ncfile, var) > 0) {
			hasLat = true;
		}
		if (getLongitudeDimensionSize(ncfile, var) > 0) {
			hasLon = true;
		}

		if (hasTime && hasLon && hasLat) {			
//			System.out.println("Compatible variable: " + var.getFullName() + " : " + getTimeDimensionSize(ncfile, var) +"/" + getLatitudeDimensionSize(ncfile, var) +"/" + getLongitudeDimensionSize(ncfile, var));
			return true;
		}
		return false;
	}

	public static Variable getTimeVariable(NetcdfFile ncfile, Variable var) {
		return getDimensionVariable(ncfile, var, timeStrings);
	}

	public static Variable getHeightVariable(NetcdfFile ncfile, Variable var) {
		return getDimensionVariable(ncfile, var, heightStrings);
	}

	public static Variable getLatitudeVariable(NetcdfFile ncfile, Variable var) {
		return getDimensionVariable(ncfile, var, latStrings);
	}

	public static Variable getLongitudeVariable(NetcdfFile ncfile, Variable var) {
		return getDimensionVariable(ncfile, var, lonStrings);
	}

	public static Variable getDimensionVariable(NetcdfFile ncfile, Variable var, String... toMatch) {
//		System.out.println("For variable: " + var.getFullName());
		List<Attribute> attributes = var.getAttributes();
		Variable result = null;
		for (Attribute a : attributes) {
			if (a.getFullName().compareTo("coordinates") == 0) {
				String[] coords = a.getStringValue().split("[ ]");
				for (String c : coords) {
					for (String m : toMatch) {
						if (c.contains(m)) {
							Variable potentials = ncfile.findVariable(c);
							if (potentials != null) {
//								System.out.println("Found coordinate: "	+ potentials.getFullName());
								result = potentials;
								break;
							}
						}
					}
				}
			}
		}

		if (result == null) {
			for (Dimension d : var.getDimensions()) {
				for (String m : toMatch) {
					if (d.getFullName().contains(m)) {
						Variable potentials = ncfile.findVariable(d
								.getFullName());
						if (potentials != null) {
//							System.out.println("Found dimension: " + potentials.getFullName());
							result = potentials;
							break;
						}
					}
				}
			}
		}

		return result;
	}

	public static int getTimeDimensionSize(NetcdfFile ncfile, Variable var) {
		return getDimensionSize(ncfile, var, timeStrings);
	}

	public static int getHeightDimensionSize(NetcdfFile ncfile, Variable var) {
		return getDimensionSize(ncfile, var, heightStrings);
	}

	public static int getLatitudeDimensionSize(NetcdfFile ncfile, Variable var) {
		return getDimensionSize(ncfile, var, latStrings);
	}

	public static int getLongitudeDimensionSize(NetcdfFile ncfile, Variable var) {
		return getDimensionSize(ncfile, var, lonStrings);
	}

	public static int getDimensionSize(NetcdfFile ncfile, Variable var, String... toMatch) {
		int result = 0;
		for (Dimension d : var.getDimensions()) {
			for (String m : toMatch) {
				if (d.getFullName().contains(m)) {
					result = d.getLength();
					break;
				}
			}
		}
		return result;
	}

}
