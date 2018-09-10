import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class Sender {
    private File file;
    private byte[] fileByteArray;
    private byte[] packetArray;
    private int startIndex = 0, endIndex = 0, packetNumber = 1 ,totalAckNumber;

    public boolean loadFile(File fileToLoad){
        file = new File(fileToLoad.getAbsolutePath());
        if(file != null){
            fileByteArray = new byte[(int)file.length()];
            try {
                FileInputStream stream = new FileInputStream(file);
                stream.read(fileByteArray);
                stream.close();
                System.out.println("File successfully converted");
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("\nError converting file into bytes.\n");
                System.exit(1);
            }
/*            for (byte i: fileByteArray) {
                System.out.print(i + " ");
            }*/
            System.out.println("\n" + fileByteArray.length + " fileByteArray length");
            totalAckNumber = fileByteArray.length / 1000 + 1;
            System.out.println(totalAckNumber + " total ack number");
            try {
                sendAckNumber();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }else{
            return false;
        }
    }

    private void makePacket(){
        startIndex = endIndex;
        endIndex = packetNumber * 1000;
        if(endIndex < fileByteArray.length){
           // System.out.println("startIndex->" + startIndex + " endIndex->" + endIndex);
            packetArray = Arrays.copyOfRange(fileByteArray, startIndex, endIndex);
        }else{
            endIndex = fileByteArray.length;
            byte[] bytes = Arrays.copyOfRange(fileByteArray, startIndex, endIndex);
            packetArray = extend(bytes);
            System.out.println("array extension");
        }
        byte[] checksum = checksum(packetArray);

        packetArray = concat(packetArray, intToTwo(packetNumber));
        packetArray = concat(packetArray, checksum);

       // System.out.println(packetArray.length + " length packet");

        packetNumber++;
        System.out.println(packetNumber - 1);
    }

    public void sendFile() throws IOException {
        while(true) {
            makePacket();
            InetAddress inetAddress = InetAddress.getByName("localhost");
            DatagramSocket socket = new DatagramSocket(config.PORT_SENDEDR);
            DatagramPacket datagramPacket = new DatagramPacket(packetArray, packetArray.length, inetAddress, config.PORT_RECEIVER);
            socket.send(datagramPacket);
            socket.close();
            System.out.println("packet sent! ->" + packetNumber + " length->" + packetArray.length);
            if(!receiveAck()){
                sendAgain();
            }
            if(isFinished()) break;
        }
    }

    private void sendAckNumber() throws IOException {
        byte[] ackNum = intToTwo(totalAckNumber);
        byte[] bytes = new byte[1000];
        ackNum = concat(bytes, ackNum);
        InetAddress inetAddress = InetAddress.getByName("localhost");
        DatagramSocket socket = new DatagramSocket(config.PORT_SENDEDR);
        DatagramPacket datagramPacket = new DatagramPacket(ackNum, ackNum.length, inetAddress, config.PORT_RECEIVER);
        socket.send(datagramPacket);
        socket.close();
        System.out.println("total ack number sent!");
    }
    // converts integer to byte array
    private byte[] intToTwo(int number) {
        byte[] bytes = new byte[2];
        bytes[0] = (byte) (number >>> 8);
        bytes[1] = (byte) number;
        return bytes;
    }


    /*
     * Concatenate two byte arrays.
     */
    public static byte[] concat(byte[] first, byte[] second) {
        byte[] to_return = new byte[first.length + second.length];
        for (int i = 0; i < first.length; i++) {
            to_return[i] = first[i];
        }
        for (int j = 0; j < second.length; j++) {
            to_return[first.length + j] = second[j];
        }
        return to_return;
    }
    //checks whether it is finished
    private boolean isFinished(){
        if((totalAckNumber + 1) == packetNumber ) {
            System.out.println("total ack number = packet number");
            return true;
        }
        return false;
    }
    //extends byte array
    private byte[] extend(byte[] bytes){
        return concat(new byte[1000 - bytes.length], bytes);

    }

    /*
    написать метод который отправляет пакет повторно
     */
    private void sendAgain() throws IOException{
        while(true) {
            InetAddress inetAddress = InetAddress.getByName("localhost");
            DatagramSocket socket = new DatagramSocket(config.PORT_SENDEDR);
            DatagramPacket datagramPacket = new DatagramPacket(packetArray, packetArray.length, inetAddress, config.PORT_RECEIVER);
            socket.send(datagramPacket);
            socket.close();
            System.out.println("packet sent again!->" + (packetNumber - 1));
            if(receiveAck()) break;
        }

    }

    private boolean receiveAck() throws IOException{
        byte[] ackArray = new byte[2];
        DatagramSocket datagramSocket = null;
        try {
            datagramSocket = new DatagramSocket(config.PORT_SENDEDR);
        } catch (SocketException e) {
          //  e.printStackTrace();
            System.out.println("timeout");
            sendAgain();
        }
        DatagramPacket datagramPacket = new DatagramPacket(ackArray, ackArray.length);
        try {
            datagramSocket.setSoTimeout(1000);
        } catch (SocketException e) {
            //e.printStackTrace();
            System.out.println("timeout");
            datagramSocket.close();
            sendAgain();

        }
        try {
            datagramSocket.receive(datagramPacket);
        } catch (IOException e) {
           // e.printStackTrace();
            System.out.println("timeout");
            datagramSocket.close();
            sendAgain();
        }
        datagramSocket.close();
        //System.out.println("ack received!!!");
        int ackInt = toInteger(ackArray);
        System.out.println(ackInt + " ack received");
        if(ackInt == packetNumber - 1){
            System.out.println("ack correct");
            return true;
        }else{
            System.out.println("ack incorrect");
            return false;
        }
    }

    public static int toInteger(byte[] bytes) {
        // pad byte[2] to byte[4]
        if (bytes.length != 4) {
            bytes = concat(new byte[2], bytes);
        }
        return ByteBuffer.wrap(bytes).getInt();
    }

    private static byte[] checksum(byte[] packet){
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        digest.update(packet);
        byte[] digest_bytes = digest.digest();
        byte[] checksum = Arrays.copyOfRange(digest_bytes, 0, 2);

        return checksum;
    }
}
