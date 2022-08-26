import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

public class EventCarListEditor {

	private static String textEncoding = "windows-1251"; // Works for NFS PS1 text files, so use it here too lol
	private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
	
	private static int datHeaderSize = 24;
	private static int entryHeaderSize = 12;
	private static int byteArraySize = 4;
	private static int byteSmallArraySize = 2;
	private static int curPos = 0;
	
	private static byte[] eighteenByte = new byte[]{00,00,00,(byte)0x80};
	private static byte[] emptyByteArray = new byte[]{00,00,00,00};
	private static byte[] emptyDoubleByteArray = new byte[]{00,00,00,00,00,00,00,00};
	
	private static byte[] datHeaderArray = new byte[]{
			00,00,00,00,00,00,00,00,(byte)0xBC,(byte)0x85,(byte)0x5D,(byte)0x7F,(byte)0xF7,(byte)0x30,(byte)0x0F,00,(byte)0x18,00,00,00};
	private static byte[] eventHeaderArray = new byte[]{
			00,00,00,00,00,00,00,00,(byte)0xED,(byte)0xFC,(byte)0xC0,(byte)0x52};
	private static byte[] carHeaderArray = new byte[]{
			00,00,00,00,00,00,00,00,(byte)0x39,(byte)0x07,(byte)0x3E,(byte)0xA4};
	private static byte[] indexEndByteArray = new byte[]{
			00,00,00,00,(byte)0xF8,(byte)0xFF,(byte)0xFF,(byte)0xFF,00,00,00,(byte)0x01,00,00,00,(byte)0x80,00,00,00,00};
	
	private static byte[] eventBottomHeaderArray = new byte[]{
			(byte)0xF5,(byte)0x30,(byte)0x0F,00,00,00,00,(byte)0x01};
	private static byte[] carBottomHeaderArray = new byte[]{
			(byte)0xAA,(byte)0x1D,(byte)0x10,00,00,00,00,(byte)0x01};
	
	//
	// dat-file read
	//
	
	public void unpackDatFile(String filePath, String outputName) throws IOException {
		byte[] datArray = Files.readAllBytes(Paths.get(filePath));
		
		// How much event entries we got?
		List<byte[]> eventOffsetsList = new ArrayList<>();
		boolean isOffsetsEnded = false;
		changeCurPos(curPos, datHeaderSize); // Skip header bytes
		while (!isOffsetsEnded) {
			byte[] checkOffsetArray = Arrays.copyOfRange(datArray, curPos, curPos + byteArraySize);
			if (!Arrays.equals(checkOffsetArray, emptyByteArray)) {
				eventOffsetsList.add(checkOffsetArray);
				changeCurPos(curPos, byteArraySize);
				// System.out.println("### passed: " + hexToString(checkOffsetArray));
			} else {
				isOffsetsEnded = true;
			}	
		}
		System.out.println("### Event offset entries: " + eventOffsetsList.size() + " found.");
		isOffsetsEnded = false;

		// Fetch all events and their car lists
		List<EventEntryObject> eventsList = new ArrayList<>();
		while (!isOffsetsEnded) {
			if (!Arrays.equals(Arrays.copyOfRange(datArray, curPos, curPos + emptyDoubleByteArray.length), emptyDoubleByteArray)) {
				isOffsetsEnded = true; // Events has been ended
				break;
			}
			EventEntryObject eventObj = new EventEntryObject();
			List<CarEntryObject> carEntriesList = new ArrayList<>();
			List<byte[]> partUnlockIdsList = new ArrayList<>();
			
			eventObj.setHeader(Arrays.copyOfRange(datArray, curPos, curPos + entryHeaderSize));
			changeCurPos(curPos, entryHeaderSize);
			//
			eventObj.setEventId(Arrays.copyOfRange(datArray, curPos, curPos + byteArraySize));
			changeCurPos(curPos, byteArraySize * 2); // Skip CarEntriesOffset
			//
			eventObj.setCarEntriesCount(Arrays.copyOfRange(datArray, curPos, curPos + byteArraySize));
			int carEntriesCount = eventObj.getCarEntriesCount()[0] & 0xFF;
			changeCurPos(curPos, byteArraySize);
			
			for (int i = 0; i < carEntriesCount; i++) {
				CarEntryObject carObj = new CarEntryObject();
				carObj.setHeader(Arrays.copyOfRange(datArray, curPos, curPos + entryHeaderSize));
				changeCurPos(curPos, entryHeaderSize);
				//
				carObj.setCarId(Arrays.copyOfRange(datArray, curPos, curPos + byteArraySize));
				changeCurPos(curPos, byteArraySize);
				//
				carObj.setUnknownArray1(Arrays.copyOfRange(datArray, curPos, curPos + byteArraySize));
				changeCurPos(curPos, byteArraySize);
				//
				byte[] partUnlockTypeIndexOffset = Arrays.copyOfRange(datArray, curPos, curPos + byteArraySize);
				carObj.setPartUnlockTypeIndex(!Arrays.equals(partUnlockTypeIndexOffset, emptyByteArray) ? 
						byteArrayToInt(partUnlockTypeIndexOffset) : 0); // Sometimes car can have zero bytes here
				changeCurPos(curPos, byteArraySize); // Temporarily save the PartUnlock offset here
				//
				carObj.setTheFFArray(Arrays.copyOfRange(datArray, curPos, curPos + entryHeaderSize));
				changeCurPos(curPos, entryHeaderSize);
				//
				carObj.setPartsAmountId(Arrays.copyOfRange(datArray, curPos, curPos + byteArraySize));
				changeCurPos(curPos, byteArraySize);
				//
				carObj.setDifficultyId(Arrays.copyOfRange(datArray, curPos, curPos + byteArraySize));
				changeCurPos(curPos, byteArraySize);
				carEntriesList.add(carObj);
			}
			
			// Now we can determine Ids of PartUnlockType arrays for each car entry
			for (CarEntryObject carObj : carEntriesList) {
				if (carObj.getPartUnlockTypeIndex() == 0) continue; // Left as it is
				carObj.setPartUnlockTypeIndex((carObj.getPartUnlockTypeIndex() - curPos) / 2);
			}
			
			// Get PartUnlock arrays on the end of Event array
			boolean isArraysEnded = false;
			while (!isArraysEnded) {
				if (Arrays.equals(Arrays.copyOfRange(datArray, curPos, curPos + indexEndByteArray.length), indexEndByteArray) ||
						Arrays.equals(Arrays.copyOfRange(datArray, curPos, curPos + entryHeaderSize), eventHeaderArray)) {
					isArraysEnded = true; // PartUnlock arrays has been ended
				} else {
					partUnlockIdsList.add(Arrays.copyOfRange(datArray, curPos, curPos + byteSmallArraySize));
					changeCurPos(curPos, byteSmallArraySize); // Note: sometimes cars can refer to arrays while skipping some of them
				} // and game uses two arrays, pointed one and the next one
			}
			eventObj.setPartUnlockIdsList(partUnlockIdsList);
			eventObj.setCarEntriesList(carEntriesList);
			eventsList.add(eventObj);
			System.out.println("### Event ID: " + hexToString(eventObj.getEventId()) + ", cars count: " + carEntriesList.size());
		}
		System.out.println("### Event entries: " + eventsList.size() + " found.");
		writeJsonOutput(eventsList, outputName);
	}
	
	public void writeJsonOutput(List<EventEntryObject> eventsList, String outputName) 
			throws JsonIOException, IOException {
		GsonBuilder builder = new GsonBuilder().disableHtmlEscaping();
        Gson gson = builder.setPrettyPrinting().create();
        
        List<EventJsonObject> eventObjList = new ArrayList<>();
		for (EventEntryObject eventByteObj : eventsList) {
			EventJsonObject eventJsonObj = new EventJsonObject();
			eventJsonObj.setEventId(hexToString(eventByteObj.getEventId()));
			
			List<String> partUnlockIdsList = new ArrayList<>();
			for (byte[] partUnlockArray : eventByteObj.getPartUnlockIdsList()) {
				partUnlockIdsList.add(hexToString(partUnlockArray));
			}
			eventJsonObj.setPartUnlockIdsList(partUnlockIdsList);
			
			List<CarJsonObject> carJsonList = new ArrayList<>();
			for (CarEntryObject carEntry : eventByteObj.getCarEntriesList()) {
				CarJsonObject carJson = new CarJsonObject();
				carJson.setCarId(hexToString(carEntry.getCarId()));
				carJson.setUnknownArray1(hexToString(carEntry.getUnknownArray1()));
				carJson.setPartUnlockTypeIndex(carEntry.getPartUnlockTypeIndex());
				carJson.setTheFFArray(hexToString(carEntry.getTheFFArray()));
				carJson.setPartsAmountId(hexToString(carEntry.getPartsAmountId()));
				carJson.setDifficultyId(hexToString(carEntry.getDifficultyId()));
				carJsonList.add(carJson);
			}
			eventJsonObj.setCarsList(carJsonList);
			eventObjList.add(eventJsonObj);
		}
		FileWriter writer = new FileWriter(outputName + ".json");
		gson.toJson(eventObjList, writer);
		writer.flush(); writer.close();
		System.out.println("### .dat-file has been unpacked!");
	}
	
	//
	// dat-file write
	//
	
	public void writeDatFileOutput(String jsonPath, String outputName) throws IOException {
	    Reader reader = Files.newBufferedReader(Paths.get(jsonPath + ".json"), Charset.forName(textEncoding));
	    List<EventJsonObject> jsonObjList = new Gson().fromJson(reader, new TypeToken<List<EventJsonObject>>(){}.getType());
	    reader.close();
	    System.out.println("### Event JSON entries: " + jsonObjList.size() + " found.");
	    
	    // Prepare file Header + amount of event entries
	    List<byte[]> datHeaderBytes = new ArrayList<>(); // 1
	    datHeaderBytes.add(datHeaderArray); datHeaderBytes.add(intToByteArrayLE(jsonObjList.size(), byteArraySize));
	    // Skip header event offsets
	    changeCurPos(curPos, (datHeaderArray.length + byteArraySize) + (jsonObjList.size() * byteArraySize));
	    
	    List<Integer> headerCurPosIdArray = new ArrayList<>(); // Header event offsets    
	    List<BottomIndexObject> bottomIndexObjList = new ArrayList<>(); // Bottom index events & cars offsets
	    
	    List<EventEntryObject> eventObjList = new ArrayList<>();
	    for (EventJsonObject eventJsonObj : jsonObjList) {
	    	EventEntryObject eventHexObj = new EventEntryObject();
	    	List<CarEntryObject> carsHexList = new ArrayList<>();
	    	headerCurPosIdArray.add(curPos);
	    	bottomIndexObjList.add(new BottomIndexObject(
		    		"event", intToByteArrayLE(curPos, byteArraySize)));
	    	//
	    	eventHexObj.setHeader(eventHeaderArray);
	    	changeCurPos(curPos, eventHeaderArray.length);
	    	//
	    	eventHexObj.setEventId(decodeHexStr(eventJsonObj.getEventId()));
	    	changeCurPos(curPos, byteArraySize * 2); // Skip CarEntriesOffset
	    	//
	    	eventHexObj.setCarEntriesCount(intToByteArrayLE(eventJsonObj.getCarsList().size(), byteArraySize));
	    	changeCurPos(curPos, byteArraySize);
	    	//
	    	eventHexObj.setCarEntriesOffset(intToByteArrayLE(curPos, byteArraySize));

	    	for (CarJsonObject carJsonObj : eventJsonObj.getCarsList()) {
	    		CarEntryObject carHexObj = new CarEntryObject();
	    		bottomIndexObjList.add(new BottomIndexObject(
	    	    		"car", intToByteArrayLE(curPos, byteArraySize))); // Cars & Events is saved on bottom index array
	    		carHexObj.setHeader(carHeaderArray);
	    		changeCurPos(curPos, carHeaderArray.length);
	    		//
	    		carHexObj.setCarId(decodeHexStr(carJsonObj.getCarId()));
	    		changeCurPos(curPos, byteArraySize);
	    		//
	    		carHexObj.setUnknownArray1(decodeHexStr(carJsonObj.getUnknownArray1()));
	    		changeCurPos(curPos, byteArraySize * 2); // Skip UnknownCarArrayOffset
	    		//
	    		carHexObj.setPartUnlockTypeIndex(carJsonObj.getPartUnlockTypeIndex());
	    		//
	    		carHexObj.setTheFFArray(decodeHexStr(carJsonObj.getTheFFArray()));
	    		changeCurPos(curPos, entryHeaderSize);
	    		//
	    		carHexObj.setPartsAmountId(decodeHexStr(carJsonObj.getPartsAmountId()));
	    		changeCurPos(curPos, byteArraySize);
	    		//
	    		carHexObj.setDifficultyId(decodeHexStr(carJsonObj.getDifficultyId()));
	    		changeCurPos(curPos, byteArraySize);
	    		carsHexList.add(carHexObj);
	    		System.out.println("### Event CAR ID: " + carJsonObj.getCarId());
	    	}
	    	eventHexObj.setCarEntriesList(carsHexList);
	    	
	    	// Collect all PartUnlock arrays of event
	    	List<byte[]> partUnlockArrayList = new ArrayList<>();
	    	List<Integer> curPosIdArray = new ArrayList<>();
	    	for (String arrayStr : eventJsonObj.getPartUnlockIdsList()) {
	    		curPosIdArray.add(curPos);
	    		partUnlockArrayList.add(decodeHexStr(arrayStr));
	    		changeCurPos(curPos, byteSmallArraySize);
	    	}
	    	eventHexObj.setPartUnlockIdsList(partUnlockArrayList);
	    	
	    	// Apply PartUnlockType offsets for car entries
	    	for (CarEntryObject carHex : carsHexList) {
	    		if (!eventHexObj.getPartUnlockIdsList().isEmpty()) {
	    			carHex.setPartUnlockOffset(intToByteArrayLE(curPosIdArray.get(
		    				carHex.getPartUnlockTypeIndex()), byteArraySize));
	    		} else { // If event doesn't have any of PartUnlockType arrays
	    			carHex.setPartUnlockOffset(emptyByteArray);
	    		}
	    	}
	    	eventObjList.add(eventHexObj);
	    	System.out.println("### Event ID: " + eventJsonObj.getEventId());
	    }
	    
	    // Fill the header event offsets
	    for (int offsetInt : headerCurPosIdArray) {	    	
	    	datHeaderBytes.add(intToByteArrayLE(offsetInt, byteArraySize));
	    }
	    
	    List<byte[]> eventObjBytes = new ArrayList<>(); // 2
	    for (EventEntryObject eventHexObj : eventObjList) {
	    	eventObjBytes.add(eventHexObj.getHeader());
	    	eventObjBytes.add(eventHexObj.getEventId());
	    	eventObjBytes.add(eventHexObj.getCarEntriesOffset());
	    	eventObjBytes.add(eventHexObj.getCarEntriesCount());
	    	for (CarEntryObject carHexObj : eventHexObj.getCarEntriesList()) {
	    		eventObjBytes.add(carHexObj.getHeader());
	    		eventObjBytes.add(carHexObj.getCarId());
	    		eventObjBytes.add(carHexObj.getUnknownArray1());
	    		eventObjBytes.add(carHexObj.getPartUnlockOffset());
	    		eventObjBytes.add(carHexObj.getTheFFArray());
	    		eventObjBytes.add(carHexObj.getPartsAmountId());
	    		eventObjBytes.add(carHexObj.getDifficultyId());
	    	}
	    	if (!eventHexObj.getPartUnlockIdsList().isEmpty()) {
	    		eventObjBytes.add(flatByteList(eventHexObj.getPartUnlockIdsList()));
	    	}
	    }
	    
	    List<byte[]> bottomIndexBytes = new ArrayList<>(); // 3
	    bottomIndexBytes.add(indexEndByteArray); // Bottom index header
	    for (BottomIndexObject bottomObj : bottomIndexObjList) {
	    	// System.out.println("### test " + bottomObj.getEntryType() + ", offset: " + hexToString(bottomObj.getOffset()));
	    	bottomIndexBytes.add(bottomObj.getEntryType().contentEquals("event") ? 
	    			eventBottomHeaderArray : carBottomHeaderArray);
	    	
	    	byte[] offsetBytes = bottomObj.getOffset();
	    	System.arraycopy(eighteenByte, 3, offsetBytes, 3, 1);
	    	bottomIndexBytes.add(offsetBytes); // XX XX XX 80 for all offsets	    	
	    	bottomIndexBytes.add(emptyByteArray);
	    }
	    
	    // Save the new .dat file
	    ByteArrayOutputStream fileOutputStream = new ByteArrayOutputStream();
	    for (byte[] data : datHeaderBytes) {fileOutputStream.write(data);}
	    for (byte[] data : eventObjBytes) {fileOutputStream.write(data);}
	    for (byte[] data : bottomIndexBytes) {fileOutputStream.write(data);}
	    try (FileOutputStream out = new FileOutputStream(outputName)) {
	        out.write(fileOutputStream.toByteArray());
	    }
	    System.out.println("### .dat-file has been repacked!");
	}
	
	//
	// Utilities
	//
	
	private void changeCurPos(int origPos, int addition) {
		curPos = origPos + addition;
		//System.out.println("### curPos: " + curPos);
	}
	
	// Taken from StackOverflow (Dhaval Rami)
	public static int byteArrayToInt(byte[] b) {
	    final ByteBuffer bb = ByteBuffer.wrap(b);
	    bb.order(ByteOrder.LITTLE_ENDIAN);
	    return bb.getInt();
	}
	
	private byte[] getDataFromOffset(String filePath, int pos, int objSize) throws IOException {
		byte[] data = new byte[objSize];
		try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
			raf.seek(pos);
			raf.readFully(data);
		} 
		catch (FileNotFoundException e) {e.printStackTrace();}
		return data;
	}
	
	private byte[] decodeHexStr(String str) {
		int len = str.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4)
					+ Character.digit(str.charAt(i+1), 16));
		}
		return data;
	}
	
	private byte[] intToByteArrayLE(int data, int size) {    
		return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).putInt(data).array(); 
	}
	private byte[] intToByteArrayBE(int data, int size) {    
		return ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN).putInt(data).array(); 
	}
	
	// Taken from StackOverflow (maybeWeCouldStealAVan)
	private String hexToString(byte[] bytes) {
	    byte[] hexChars = new byte[bytes.length * 2];
	    for (int j = 0; j < bytes.length; j++) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = HEX_ARRAY[v >>> 4];
	        hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
	    }
	    return new String(hexChars, StandardCharsets.UTF_8);
	}
	
	// Taken from StackOverflow (Nir Alfasi)
	private static byte[] flatByteList(List<byte[]> list) {
	    int byteArrlength = list.get(0).length;
	    byte[] result = new byte[list.size() * byteArrlength]; // since all the arrays have the same size
	    for (int i = 0; i < list.size(); i++) {
	        byte[] arr = list.get(i);
	        for (int j = 0; j < byteArrlength; j++) {
	            result[i * byteArrlength + j] = arr[j];
	        }
	    }
	    return result;
	}
	
	//
	// Objects
	//
	
	public static class EventEntryObject {
		private byte[] header; 
		private byte[] eventId; 
		private byte[] carEntriesOffset;
		private byte[] carEntriesCount; 
		private List<CarEntryObject> carEntriesList; 
		private List<byte[]> partUnlockIdsList;
		
		public byte[] getHeader() {
			return header;
		}
		public void setHeader(byte[] header) {
			this.header = header;
		}
		
		public byte[] getEventId() {
			return eventId;
		}
		public void setEventId(byte[] eventId) {
			this.eventId = eventId;
		}
		
		public byte[] getCarEntriesOffset() {
			return carEntriesOffset;
		}
		public void setCarEntriesOffset(byte[] carEntriesOffset) {
			this.carEntriesOffset = carEntriesOffset;
		}
		
		public byte[] getCarEntriesCount() {
			return carEntriesCount;
		}
		public void setCarEntriesCount(byte[] carEntriesCount) {
			this.carEntriesCount = carEntriesCount;
		}
		
		public List<CarEntryObject> getCarEntriesList() {
			return carEntriesList;
		}
		public void setCarEntriesList(List<CarEntryObject> carEntriesList) {
			this.carEntriesList = carEntriesList;
		}
		
		public List<byte[]> getPartUnlockIdsList() {
			return partUnlockIdsList;
		}
		public void setPartUnlockIdsList(List<byte[]> partUnlockIdsList) {
			this.partUnlockIdsList = partUnlockIdsList;
		} 
	}
	
	public static class CarEntryObject {
		private byte[] header; 
		private byte[] carId; 
		private byte[] unknownArray1;
		private byte[] partUnlockOffset; 
		private int partUnlockTypeIndex;
		private byte[] theFFArray; 
		private byte[] partsAmountId;
		private byte[] difficultyId;
		
		public byte[] getHeader() {
			return header;
		}
		public void setHeader(byte[] header) {
			this.header = header;
		}
		
		public byte[] getCarId() {
			return carId;
		}
		public void setCarId(byte[] carId) {
			this.carId = carId;
		}
		
		public byte[] getUnknownArray1() {
			return unknownArray1;
		}
		public void setUnknownArray1(byte[] unknownArray1) {
			this.unknownArray1 = unknownArray1;
		}
		
		public byte[] getPartUnlockOffset() {
			return partUnlockOffset;
		}
		public void setPartUnlockOffset(byte[] partUnlockOffset) {
			this.partUnlockOffset = partUnlockOffset;
		}
		
		public int getPartUnlockTypeIndex() {
			return partUnlockTypeIndex;
		}
		public void setPartUnlockTypeIndex(int partUnlockTypeIndex) {
			this.partUnlockTypeIndex = partUnlockTypeIndex;
		}
		
		public byte[] getTheFFArray() {
			return theFFArray;
		}
		public void setTheFFArray(byte[] theFFArray) {
			this.theFFArray = theFFArray;
		}
		
		public byte[] getPartsAmountId() {
			return partsAmountId;
		}
		public void setPartsAmountId(byte[] partsAmountId) {
			this.partsAmountId = partsAmountId;
		}
		
		public byte[] getDifficultyId() {
			return difficultyId;
		}
		public void setDifficultyId(byte[] difficultyId) {
			this.difficultyId = difficultyId;
		}
	}
	
	public static class BottomIndexObject {
		private String entryType; 
		private byte[] offset;
		
		public BottomIndexObject(String entryType, byte[] offset) {
			this.entryType = entryType;
			this.offset = offset;
		}

		public String getEntryType() {
			return entryType;
		}
		public void setEntryType(String entryType) {
			this.entryType = entryType;
		}
		
		public byte[] getOffset() {
			return offset;
		}
		public void setOffset(byte[] offset) {
			this.offset = offset;
		}
	}
	
	public static class EventJsonObject {
		@SerializedName("EventId")
		private String eventId; 
		@SerializedName("Cars")
		private List<CarJsonObject> carsList; 
		@SerializedName("PartUnlockIds")
		private List<String> partUnlockIdsList;
		
		public String getEventId() {
			return eventId;
		}
		public void setEventId(String eventId) {
			this.eventId = eventId;
		}
		
		public List<CarJsonObject> getCarsList() {
			return carsList;
		}
		public void setCarsList(List<CarJsonObject> carsList) {
			this.carsList = carsList;
		}
		
		public List<String> getPartUnlockIdsList() {
			return partUnlockIdsList;
		}
		public void setPartUnlockIdsList(List<String> partUnlockIdsList) {
			this.partUnlockIdsList = partUnlockIdsList;
		} 
	}
	
	public static class CarJsonObject {
		@SerializedName("CarId")
		private String carId; 
		@SerializedName("UnknownArray1")
		private String unknownArray1;
		@SerializedName("PartUnlockTypeIndex")
		private int partUnlockTypeIndex;
		@SerializedName("TheFFArray")
		private String theFFArray;
		@SerializedName("PartsAmountId")
		private String partsAmountId;
		@SerializedName("DifficultyId")
		private String difficultyId;
		
		public String getCarId() {
			return carId;
		}
		public void setCarId(String carId) {
			this.carId = carId;
		}
		
		public String getUnknownArray1() {
			return unknownArray1;
		}
		public void setUnknownArray1(String unknownArray1) {
			this.unknownArray1 = unknownArray1;
		}
		
		public int getPartUnlockTypeIndex() {
			return partUnlockTypeIndex;
		}
		public void setPartUnlockTypeIndex(int partUnlockTypeIndex) {
			this.partUnlockTypeIndex = partUnlockTypeIndex;
		}
		
		public String getTheFFArray() {
			return theFFArray;
		}
		public void setTheFFArray(String theFFArray) {
			this.theFFArray = theFFArray;
		}
		
		public String getPartsAmountId() {
			return partsAmountId;
		}
		public void setPartsAmountId(String partsAmountId) {
			this.partsAmountId = partsAmountId;
		}
		
		public String getDifficultyId() {
			return difficultyId;
		}
		public void setDifficultyId(String difficultyId) {
			this.difficultyId = difficultyId;
		}
	}
	
}
