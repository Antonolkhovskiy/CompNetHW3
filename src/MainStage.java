import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;

public class MainStage extends Application {
    private ListView<String> listView;
    private VBox mainLayout;
    private Scene scene;
    private Button chooseFileButton, saveFileButton, startButton;
    private Label labelFrom, labelTo;
    private String pathFrom = null, pathTo = null;
    private HBox lablesBox, buttonsBox;
    private File fileToSend, fileToSave;


    private Sender sender;
    private static int totalAckNumber, ackInt = 0, expectedAck = 1;
    private static byte[] outputArray = {};

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        chooseFileButton = new Button("Choose File");
        saveFileButton = new Button("Choose Destination Folder");
        saveFileButton.setDisable(true);
        startButton = new Button("Send");
        startButton.setDisable(true);
        listView = new ListView<>();

        lablesBox = new HBox();
        lablesBox.setAlignment(Pos.CENTER);
        labelFrom = new Label("");
        labelTo = new Label("");
        lablesBox.getChildren().addAll(labelFrom, labelTo);
        buttonsBox = new HBox();
        buttonsBox.setAlignment(Pos.CENTER);
        buttonsBox.getChildren().addAll(chooseFileButton, saveFileButton,startButton);
        mainLayout = new VBox();
        mainLayout.setPrefSize(700,450);
        mainLayout.setAlignment(Pos.CENTER);
        mainLayout.getChildren().addAll(lablesBox, listView, buttonsBox);





        scene = new Scene(mainLayout);

        primaryStage.setTitle("Computer Networks Homework III");
        primaryStage.setScene(scene);
        primaryStage.show();

        chooseFileButton.setOnAction(e->{
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Choose File To Send");
            fileToSend = fileChooser.showOpenDialog(primaryStage);

            System.out.println(fileToSend.getName() + " " + fileToSend.getAbsolutePath());

            pathFrom = new String(fileToSend.getAbsolutePath());

            getStarted();

            Platform.runLater(()->{
               labelFrom.setText(pathFrom);
               saveFileButton.setDisable(false);
            });

        });

        saveFileButton.setOnAction(e->{
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save File To");
            fileChooser.setInitialFileName(fileToSend.getName());
            fileToSave = fileChooser.showSaveDialog(primaryStage);

            System.out.println(fileToSave.getName() + " " + fileToSave.getAbsolutePath() + " to Save");

            pathTo = new String("      Transfering To      " + fileToSave.getAbsolutePath());

            Platform.runLater(()->{
                labelTo.setText(pathTo);
                startButton.setDisable(false);
            });
        });

        startButton.setOnAction(e->{
            sender = new Sender();
            Thread receiverThread = new Thread(()-> receive());
            Thread senderThread = new Thread(()-> {
                if(sender.loadFile(fileToSend)){
                    log("File was loaded. Sending started...");
                    try {
                        sender.sendFile();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                        log("Error while sending file");
                    }
                }else{
                    log("Error loading file");
                }
            });
            receiverThread.start();
            senderThread.start();

            chooseFileButton.setDisable(true);
            saveFileButton.setDisable(true);
            startButton.setDisable(true);
        });


    }

    public  void receive(){

        DatagramSocket datagramSocket = null;
        byte[] messageArray = new byte[1004];
        DatagramPacket datagramPacket = null;

        //receiving total ack number
        try {
            datagramSocket = new DatagramSocket(config.PORT_RECEIVER);
            datagramPacket = new DatagramPacket(messageArray, messageArray.length);
            //messageArray = new byte[1002];
            datagramSocket.receive(datagramPacket);
            datagramSocket.close();
            byte[] bytes = Arrays.copyOfRange(messageArray, 1000, 1002);
           // byte[] checksum = Arrays.copyOfRange(messageArray, 1002, 1004);
            totalAckNumber = toInteger(bytes);

        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //receiving file
        while (totalAckNumber > ackInt) {
            try {
                messageArray = new byte[1005];
                datagramSocket = new DatagramSocket(config.PORT_RECEIVER);
                datagramPacket = new DatagramPacket(messageArray, messageArray.length);
                datagramSocket.setSoTimeout(1000);
                datagramSocket.receive(datagramPacket);
                datagramSocket.close();
            } catch (IOException e) {
                //e.printStackTrace();
                log("Packet loss. Sending ack again");
                try {
                    datagramSocket.close();
                    sendAck(ackInt);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }

            byte[] ack = Arrays.copyOfRange(messageArray, 1000, 1002);
            byte[] checksum = Arrays.copyOfRange(messageArray, 1002, 1005);
            byte[] data = Arrays.copyOfRange(messageArray, 0, 1000);


            ackInt = toInteger(ack);

            //System.out.println(ackInt + " ack");
            if((ackInt == expectedAck) && (checksum(data, checksum))) {
                try {
                   // System.out.println("received ack and checksum are correct");
                    log("Received packet. 'ack: " + ackInt + "'" + " out of " + totalAckNumber);
                    expectedAck++;
                    outputArray = concat(outputArray, Arrays.copyOfRange(messageArray, 0, 1000));
                    sendAck(ackInt);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else{
                try {
                    //System.out.println("received ack incorrect");
                    log("Received wrong packet 'ack: " + ackInt + "'" + " expected ack: " + expectedAck);
                    sendAck(ackInt);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }


        }
        saveFile();


    }

    private  void sendAck(int ack) throws IOException{

        byte[] bytes = intToTwo(ack);
        InetAddress inetAddress = InetAddress.getByName("localhost");
        DatagramSocket socket = new DatagramSocket(config.PORT_RECEIVER);
        DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length, inetAddress, config.PORT_SENDEDR);
        socket.send(datagramPacket);
        socket.close();
        log("Ack " + ack + " sent");
    }


    /*
     * Converts a byte array to an integer.
     */
    public  int toInteger(byte[] bytes) {
        // pad byte[2] to byte[4]
        if (bytes.length != 4) {
            bytes = concat(new byte[2], bytes);
        }
        return ByteBuffer.wrap(bytes).getInt();
    }

    /*
     * Concatenate two byte arrays.
     */
    public  byte[] concat(byte[] first, byte[] second) {
        byte[] to_return = new byte[first.length + second.length];
        for (int i = 0; i < first.length; i++) {
            to_return[i] = first[i];
        }
        for (int j = 0; j < second.length; j++) {
            to_return[first.length + j] = second[j];
        }
        return to_return;
    }
    /*
     * Converts number to byte array[2]
     */
    private static byte[] intToTwo(int number) {
        byte[] bytes = new byte[2];
        bytes[0] = (byte) (number >>> 8);
        bytes[1] = (byte) number;
        return bytes;
    }

    private  void saveFile(){
        try{
            byte[] output = cleanBytes(outputArray);
            FileOutputStream stream = new FileOutputStream(fileToSave.getAbsolutePath());
            stream.write(output);
            stream.close();
            log(fileToSave.getAbsolutePath() + " successfully received");

        } catch (Exception e) {
            e.printStackTrace();
            log("Error creating output file");
        }

        Platform.runLater(()->chooseFileButton.setDisable(false));
    }

    /*
     * Remove the useless (byte)0's from the start of the bytes_received array.
     */
    private static byte[] cleanBytes(byte[] bytes_received) {

        int start_index = -1;
        for (int i = 0 ; i <=  bytes_received.length; i++){
            if (bytes_received[i] == (byte)0){
                start_index = i;
            }
            else break;
        }
        if (start_index > 0)
            return Arrays.copyOfRange(bytes_received, start_index + 1, bytes_received.length);
        else return bytes_received;
    }

    private  boolean checksum(byte[] data, byte[] checksum){

        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(data);
            byte[] to_compare = Arrays.copyOfRange(digest.digest(), 0, 2);
            if (checksum[0] == to_compare[0] && checksum[1] == to_compare[1]) {
                //System.out.println("checksum true");
                //log("Checksum for " + ackInt + " is True");
                return true;
            } else {
                //System.out.println("checksum false");
                log("Checksum for " + ackInt + " is False");
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("\nChecksum error encountered\n");
        }
        log("Checksum false");
        return false;
    }

    public void log(String message){

        Platform.runLater(()->{
            listView.getItems().add(message);
            listView.scrollTo(listView.getItems().size());
        });
    }

    public void getStarted(){
        Platform.runLater(()->{
            listView.getItems().clear();
            startButton.setDisable(true);
            saveFileButton.setDisable(true);
            chooseFileButton.setDisable(false);
        });
    }



}
