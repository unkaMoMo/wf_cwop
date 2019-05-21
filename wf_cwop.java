package wf_cwop;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.Security;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Properties;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jim
 */
class wf_cwop {
    
    static float[] precipHourList;
    private static String msg;    
    private static float WF_StationPressure;
    private static float WF_Temperature;
    private static float WF_Humidity;
    private static float WF_RapidWind;
    private static int WF_RapidWind_Dir;    
    private static float WF_RainThisMinute = 0;
    private static float WF_windAvg;
    private static float WF_windGust;
    private static float WF_windDirectionAvg;
    private static float WF_SolarRadiation;
    private static int hourIndex = 0;
    private static float rainHour;
    private static int Index24 = 0;    
    private static Properties props;
    private static String SkySerial;
    private static String AirSerial;    
    private static boolean cwopUpload;
    private static float elevation;//Elevation in meters
    private static int uploadInterval;
    private static boolean firstRun = true;
    private static long oldTime;
    private static float cwoprainHour;
    private static float[] rain24H;
    private static float[] tempCList;
    private static float cwop24H;
    private static long timeStamp;
    private static float[] humList;
    private static int tempIndex;
    private static float cwopTempC;
    private static boolean haveHum;
    private static boolean haveRain;
    private static boolean haveTemp;
    private static int humIndex;
    private static float cwopHum;
    private static StringBuffer bf;
    private static String LAT;
    private static String LONG;
    private static String cwopid;
    private static double altimeter;
    private static String outgoing;
    private static String login;
    private static String server;
    private static Calendar currentCal = Calendar.getInstance();
    private static int currentDay;
    private static float rainSinceMidnight;
    
    public static void main(String[] args) throws IOException {
        Security.setProperty("networkaddress.cache.ttl", "0");
        Security.setProperty("networkaddress.negative.cache.ttl", "0");
        try {
            String propsFile;
            if (args.length == 0) {
                propsFile = "wf_cwop.conf";
            } else {
                propsFile = args[0];
            }
            (wf_cwop.props = new Properties()).load(new FileInputStream(propsFile));
        } catch (FileNotFoundException ex) {
            System.out.println("The Properties file wasn't found.");
            System.out.println("Check that the file:'wf_cwop.conf' is in the wf_cwop directory, ");
            System.out.println("or specify the name of your properties file as a command line argument if different.");
            System.out.println("System will now exit");
            System.exit(0);
        }
        wf_cwop.precipHourList = new float[60];
        wf_cwop.rain24H = new float[1440];
        wf_cwop.tempCList = new float[5];
        wf_cwop.humList = new float[5];
        String string;
        DatagramSocket datagramSocket = new DatagramSocket(50222);
        byte[] b = new byte[512];
        DatagramPacket datagramPacket = new DatagramPacket(b, 512);
        SkySerial = props.getProperty("SkySerial");
        AirSerial = props.getProperty("AirSerial");
        uploadInterval = Integer.parseInt(props.getProperty("uploadInterval"));
        elevation = Float.parseFloat(props.getProperty("elevation"));
        LAT = props.getProperty("LAT");
        LONG = props.getProperty("LONG");
        cwopid = props.getProperty("cwopid");
        server = props.getProperty("server");
        while (true) {            
            uploadTimer();
            datagramSocket.receive(datagramPacket);            
            byte[] data = datagramPacket.getData();            
            string = new String(data, 0, datagramPacket.getLength());
            if (string.contains("hub_status")) {
                msg = string;
                parseHub(msg);
            } //if (string.contains("rapid_wind")&& string.contains(SkySerial)) {
            //msg = string.split(":")[4].split("\\]}")[0].split("\\[")[1];
            //System.out.println("Rapid_Wind " + msg);
            //parseRapid_Wind(msg);
            //}
            else if (string.contains("obs_air") && string.contains(AirSerial)) {
                msg = string.split(":")[4].split("\\]\\]")[0].split("\\[\\[")[1];
                
                parseAir(msg);
            } else if (string.contains("obs_sky") && string.contains(SkySerial)) {
                msg = string.split(":")[4].split("\\]\\]")[0].split("\\[\\[")[1];                
                parseSky(msg);
            }            
        }
    }
    
    private static void uploadTimer() {
        if (timeStamp > 0 && timeStamp >= oldTime + uploadInterval) {
            cwopUpload = true;
            System.out.println("Time to upload");
            oldTime = timeStamp;
            cwopUpload();
        }
        
    }
    
    private static void init() {//Fill the arrays with the initail reading
        if (haveTemp && haveRain && haveHum && firstRun) {
            for (int i = 0; i < humList.length; i++) {
                humList[i] = WF_Humidity;
                
            }
            for (int i = 0; i < tempCList.length; i++) {
                tempCList[i] = WF_Temperature;
            }            
            firstRun = false;            
        }
    }
    
    private static void cwopHum() {
        if (firstRun) {
            haveHum = true;
            init();
        }
        if (!firstRun) {
            if (humIndex >= 5) {
                humIndex = 0;
            }
            humList[humIndex] = WF_Humidity;            
            for (int i = 0; i < humList.length - 1; i++) {
                cwopHum = cwopHum + humList[i];
            }            
            cwopHum = (cwopHum / humList.length);            
            humIndex++;
        }
    }
    
    private static void parseHub(String msg) {
        timeStamp = Long.parseLong(msg.split(":")[6].split(",")[0]);
        if (firstRun) {
            oldTime = timeStamp;
            currentDay = Integer.parseInt(new SimpleDateFormat("dd").format(new Date(timeStamp * 1000)));            
            System.out.println("First run = " + firstRun + " Today is:" + currentDay);
        }
    }
    
    private static void parseRapid_Wind(String msg) {
        try {
            WF_RapidWind = Float.parseFloat(msg.split(",")[1]);
            System.out.println("Rapid wind = " + WF_RapidWind);
            WF_RapidWind_Dir = Integer.parseInt(msg.split(",")[2]);
            System.out.println("Rapid wind direction = " + WF_RapidWind_Dir);
        } catch (NumberFormatException numberFormatException) {
            System.err.println("Null entry " + numberFormatException);
        }
    }
    
    private static void parseAir(String msg) {
        try {            
            WF_StationPressure = Float.parseFloat(msg.split(",")[1]);
            System.out.println("Station pressure = " + WF_StationPressure);
            WF_Temperature = Float.parseFloat(msg.split(",")[2]);
            cwopTempC();
            System.out.println("Temperature C = " + WF_Temperature);
            WF_Humidity = Integer.parseInt(msg.split(",")[3]);
            cwopHum();
            System.out.println("Humidity = " + WF_Humidity);            
        } catch (NumberFormatException numberFormatException) {
            System.err.println("Caught a 'null' entry " + numberFormatException);
        }
    }
    
    private static void parseSky(String msg) {
        try {            
            WF_RainThisMinute = Float.parseFloat(msg.split(",")[3]);
            System.out.println("Rain in last min. = " + WF_RainThisMinute);            
            CWOPRain();            
            System.out.println("Cwop upload= " + cwopUpload);
            WF_windAvg = Float.parseFloat(msg.split(",")[5]);
            System.out.println("Wind average = " + WF_windAvg);
            WF_windGust = Float.parseFloat(msg.split(",")[6]);
            System.out.println("Wind gust = " + WF_windGust);
            WF_windDirectionAvg = Float.parseFloat(msg.split(",")[7]);
            System.out.println("Wind direction = " + WF_windDirectionAvg);
            WF_SolarRadiation = Float.parseFloat(msg.split(",")[10]);            
        } catch (NumberFormatException numberFormatException) {
            System.err.println("Caught a 'null' entry " + numberFormatException);
        }
    }
    
    private wf_cwop() {
        
    }
    
    private static void cwopTempC() {
        if (firstRun) {
            haveTemp = true;
            init();
        }
        if (!firstRun) {
            if (tempIndex >= 5) {
                tempIndex = 0;
            }
            tempCList[tempIndex] = WF_Temperature;            
            for (int i = 0; i < tempCList.length - 1; i++) {
                cwopTempC = cwopTempC + tempCList[i];
            }
            cwopTempC = cwopTempC / tempCList.length;
            tempIndex++;
        }
    }
    
    private static void CWOPRain() {
        if (firstRun) {
            haveRain = true;
            init();            
        }
        if (!firstRun) {
            rainSinceMidnight += WF_RainThisMinute;
            if (Integer.parseInt(new SimpleDateFormat("dd").format(new Date(timeStamp * 1000))) != currentDay) {
                rainSinceMidnight = 0;
                currentDay = Integer.parseInt(new SimpleDateFormat("dd").format(new Date(timeStamp * 1000)));
                System.out.println("Rain since midnight reset to zero");
            }
            if (hourIndex >= 59) {
                hourIndex = 0;
            }
            if (Index24 >= 1439) {
                Index24 = 0;
            }
            precipHourList[hourIndex] = WF_RainThisMinute;
            rainHour = 0;
            for (int i = 0; i < precipHourList.length - 1; i++) {
                rainHour += precipHourList[i];
            }
            System.out.println("Rain this hour = " + rainHour * 0.039370078740157 + " inches");
            System.out.println("Rain since midnight = " + rainSinceMidnight * 0.039370078740157 + " inches");
            cwop24H = 0;
            cwoprainHour = rainHour;
            rain24H[Index24] = WF_RainThisMinute;
            for (int i = 0; i < rain24H.length - 1; i++) {
                cwop24H += rain24H[i];
            }
            Index24++;
            System.out.println("Rain 24 hours = " + cwop24H * 0.03937007874015 + " inches");
            hourIndex++;
            WF_RainThisMinute = 0;            
        }
    }
    
    private static void cwopUpload() {
        bf = new StringBuffer();
        cwopUpload = false;
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int hour = cal.get(Calendar.HOUR_OF_DAY);         // 0..11
        int minutes = cal.get(Calendar.MINUTE);      // 0..59        
        DecimalFormat myFormatter2 = new DecimalFormat("00");//two digits for humidity
        DecimalFormat myFormatter3 = new DecimalFormat("000"); //three digits with leading zero for direction, rain      
        DecimalFormat myFormatter5 = new DecimalFormat("00000");//five digits for barometer
        //Build the CWOP String
        bf.append(cwopid).append(">APRS,TCPIP*:");
        if (day < 10) {
            bf.append("@" + "0" + day);
        } else {
            bf.append("@" + day);
        }
        if (hour < 10) {
            bf.append("0" + hour);
        } else {
            bf.append(hour);
        }
        if (minutes < 10) {
            bf.append("0" + minutes);
        } else {
            bf.append(minutes);
        }
        bf.append("z" + LAT + "/" + LONG);
        bf.append("_").append(myFormatter3.format(WF_windDirectionAvg)).append("/");
        bf.append(myFormatter3.format(WF_windAvg)).append("g").append(myFormatter3.format(WF_windGust));
        bf.append("t").append(myFormatter3.format(cwopTempC * 1.8 + 32));
        bf.append("r").append(myFormatter3.format((cwoprainHour * 0.03937007874015) * 100));
        bf.append("p").append(myFormatter3.format((cwop24H * 0.03937007874015) * 100));
        bf.append("P").append(myFormatter3.format((rainSinceMidnight * 0.03937007874015) * 100));
        if (myFormatter2.format(cwopHum).toString().equals("100")) {
            
            bf.append("h00");
        } else {
            bf.append("h").append(myFormatter2.format(cwopHum));
        }
        double stationPressure = WF_StationPressure / 33.864;
        altimeter = Math.pow((Math.pow(stationPressure, .1903) + 1.313E-5 * elevation * 3.28084), (1 / .1903));
        altimeter *= 33.864;
        bf.append("b").append(myFormatter5.format(altimeter * 10));
        float l = WF_SolarRadiation;
        if (l < 1000) {
            bf.append("L").append(myFormatter3.format(l));
        } else if (l >= 1000) {
            l -= 1000;
            bf.append("l").append(myFormatter3.format(l));
        }
        //bf.append("X...");
        bf.append(".eWeatherFlow/WF_CWOP-1.0\r\n");        
        login = "user " + cwopid + " pass -1 vers WF_CWOP 1.0\r\n";
        outgoing = bf.toString();
        outgoing.trim();
        login.trim();
        upLoad(login, outgoing);
    }
    
    private static void upLoad(String login, String outgoing) {
        
        int port = 14580;
        
        {
            try {
                Socket socket1 = new Socket();
                socket1.connect(new InetSocketAddress(server, port), 10000);
                socket1.setSoTimeout(10000);
                if (socket1.isConnected()) {
                    System.out.println("Trying Server: " + server + " On Port " + port);
                    InputStream in = socket1.getInputStream();
                    InputStreamReader isr;
                    BufferedReader br;
                    try (BufferedOutputStream outputStream = new BufferedOutputStream(socket1.getOutputStream())) {
                        isr = new InputStreamReader(in);
                        br = new BufferedReader(isr);
                        String line1;
                        if ((line1 = br.readLine()) != null) {
                            line1.trim();
                            System.out.println("CWOP connection response is: " + line1);
                        }
                        line1 = "";
                        Thread.sleep(2000);
                        System.out.println("Sending CWOP logon string");
                        outputStream.write(login.getBytes());
                        outputStream.flush();
                        if ((line1 = br.readLine()) != null) {
                            line1.trim();
                            if (!line1.contains(login.split(" ")[1])) {
                                System.out.println("CWOP Login Failed");
                                throw new Exception("Server busy");
                            }
                            Thread.sleep(2000);
                            System.out.println("CWOP logon response is: " + line1);
                        }
                        System.out.println("Sending data string to CWOP ");
                        outputStream.write(outgoing.getBytes());
                        System.out.println(outgoing);
                        outputStream.flush();
                        Thread.sleep(2000);
                        System.out.println("CWOP apparently posted OK");
                    }
                    isr.close();
                    br.close();
                    socket1.close();
                    outgoing = null;
                    bf = null;
                }
            } catch (IOException ex) {
                Logger.getLogger(wf_cwop.class.getName()).log(Level.SEVERE, null, ex);
            } catch (Exception ex) {
                Logger.getLogger(wf_cwop.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
    }
    
}
