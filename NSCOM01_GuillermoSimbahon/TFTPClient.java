
/**
 * Project:         NSCOM01 MCO1
 * Submitted by:    Guillermo, Eugene S.
 *                  Simbahon, Joolz Ryane C.
 * Last Modified:   Most Recent Version
 */

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Random;

public class TFTPClient {
    private static final int OPCODE_RRQ = 1;
    private static final int OPCODE_WRQ = 2;
    private static final int OPCODE_DATA = 3;
    private static final int OPCODE_ACK = 4;
    private static final int OPCODE_ERR = 5;
    private static final int OPCODE_OACK = 6;

    private static DatagramPacket sendPacket = null;
    private static DatagramPacket recvPacket = null;
    private int BLOCK_SIZE = 512;
    private byte[] buffer = new byte[BLOCK_SIZE + 4];

    public static final int INIT_TID = 69;
    public static final String MODE_OCTET = "octet";

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        int choice = -1;
        String file = new String();
        String fileSer = null;
        TFTPClient client = new TFTPClient();

        System.out.println("\nTFTP Client is now open...");

        System.out.print("Enter Server Address: ");
        String ipAddr = scanner.nextLine();

        System.out.println("\nSELECT MODE\n");
        System.out.println("[1] Download File");
        System.out.println("[2] Upload File\n");
        System.out.println("[0] Exit\n");
        System.out.println("---------------\n");

        while (choice != 1 && choice != 2 && choice != 0) {
            System.out.print("[CHOICE]: ");
            choice = Integer.parseInt(scanner.next());
        }

        if (choice != 0) {
            int timeout = 0;
            do {
                System.out.println(
                        "Would you like a timeout? If yes, please specify the amount in seconds. If not, please input 0:");
                try {
                    timeout = Integer.parseInt(scanner.next());
                } catch (NumberFormatException e) {
                    timeout = 0;
                    System.err.println(e);
                    System.out.println("No timeout implemented");
                }
            } while (timeout < 0);
            timeout *= 1000;

            if (choice == 1) {
                System.out.println("Enter the File Name and Path: ");
                file = scanner.next();
                System.out.println("Chosen file: " + file);
                client.downloadFile(file, ipAddr, timeout);
            } else {
                System.out.println("Enter the File Name and Path: ");
                file = scanner.next();
                System.out.println("Chosen file: " + file);

                System.out.print("Enter the File Name To Be Used in the Server: ");
                fileSer = scanner.next();
                System.out.println("Chosen filename: " + fileSer);

                try {
                    client.uploadFile(file, fileSer, ipAddr, timeout);
                } catch (IOException e) {
                    System.out.println(e);
                }
            }
        }
        System.out.println("\nTFTP Client is closing...");
        scanner.close();
    }

    private void downloadFile(String filename, String ipAddr, int timeout) throws IOException {
        DatagramSocket UDPSock = null;
        // Rand.0.omly generated source TID
        int sourceTID = generateSourceTID(0, 65535);
        int fileSize = 0;

        // Binds to port number which is equal to randomly generated source TID
        try {
            UDPSock = new DatagramSocket(sourceTID);
        } catch (Exception e) {
            System.out.println("Socket Creation Error" + e.toString());
            return;
        }

        try {
            fileSize = (int) Files.size(Paths.get(filename));
        } catch (NoSuchFileException e) {
        }

        // Creates RRQ Packet
        sendPacket = createRRQPacket(filename, ipAddr, fileSize);

        // Sends RRQ Packet
        try {
            UDPSock.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (timeout != 0) {
            UDPSock.setSoTimeout(timeout);
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = null;
        do {
            recvPacket = new DatagramPacket(buffer, buffer.length);
            try {
                UDPSock.receive(recvPacket);
            } catch (SocketTimeoutException e) {
                // Handle the timeout error
                System.err.println("[ERROR]: Server unresponsive");
                UDPSock.close();
                return;
            }

            int opCode = getOpcode(recvPacket);

            if (opCode == OPCODE_ERR) {
                displayError(recvPacket); // If error packet is received, prints error
                UDPSock.close();
                return;
            } else if (opCode == OPCODE_OACK) {

                byte[] oack = recvPacket.getData();
                byte[] string = new byte[30];
                int offset = 2, index = 0;// , fileSize;
                while (offset < recvPacket.getLength()) {
                    while (oack[offset] != 0) {
                        string[index] = oack[offset];
                        index++;
                        offset++;
                    }
                    offset++;

                    if (new String(string, 0, index).equals("blksize")) {
                        index = 0;
                        while (oack[offset] != 0) {
                            string[index] = oack[offset];
                            index++;
                            offset++;
                        }
                        offset++;

                        try {
                            BLOCK_SIZE = Integer.parseInt(new String(string, 0, index));
                        } catch (Exception e) {
                            System.err.println(e);
                        }
                    } else if (new String(string, 0, index).equals("tsize")) {
                        index = 0;
                        while (oack[offset] != 0) {
                            string[index] = oack[offset];
                            index++;
                            offset++;
                        }
                        offset++;
                        try {
                            fileSize = Integer.parseInt(new String(string, 0, index));
                        } catch (Exception e) {
                            System.err.println(e);
                        }
                    }
                    index = 0;
                }

                sendPacket = createACKPacket(0, ipAddr, recvPacket);
                try {
                    UDPSock.send(sendPacket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (opCode == OPCODE_DATA) {
                dos = new DataOutputStream(bos);
                try {
                    dos.write(recvPacket.getData(), 4, recvPacket.getLength() - 4);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // Send ACK packet
                sendPacket = createACKPacket(getBlockNum(recvPacket), ipAddr, recvPacket);
                try {
                    UDPSock.send(sendPacket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        } while (!(recvPacket.getLength() < BLOCK_SIZE + 4 && getOpcode(recvPacket) == OPCODE_DATA));

        // Write file
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(filename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            bos.writeTo(outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("File downloaded.");
        outputStream.close();
        UDPSock.close();
    }

    private DatagramPacket createRRQPacket(String filename, String ipAddr, int fileSize) {
        DatagramPacket rrqPacket = null;
        ByteBuffer buffer = null;
        int input = 0;
        String input2 = null;
        Scanner in = new Scanner(System.in);

        do {
            System.out.println(
                    "Enter block size (8 - 65464). To continue with default block size, input 0.");
            input = Integer.valueOf(in.nextLine());
        } while (!(input >= 8 && input <= 65464 || input == 0));

        do {
            System.out.println("Would you like to include the tsize option (y/n)?");
            input2 = in.nextLine();
        } while (!(input2.equals("y") || input2.equals("n")));

        if (input == 0) {
            if (input2.equals("n")) {
                this.BLOCK_SIZE = 512;
                buffer = ByteBuffer.allocate(2 + filename.length() + 1 + MODE_OCTET.length() + 1);
                buffer.putShort((short) OPCODE_RRQ);
                buffer.put(filename.getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put(MODE_OCTET.getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
            } else {
                this.BLOCK_SIZE = 512;
                buffer = ByteBuffer.allocate(2 + filename.length() + 1 + MODE_OCTET.length() + 1 +
                        "tsize".length() + 1 + Integer.toString(fileSize).length() + 1);
                buffer.putShort((short) OPCODE_RRQ);
                buffer.put(filename.getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put(MODE_OCTET.getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put("tsize".getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put(Integer.toString(fileSize).getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
            }

        } else {
            if (input2.equals("n")) {
                this.BLOCK_SIZE = input;
                buffer = ByteBuffer.allocate(2 + filename.length() + 1 + MODE_OCTET.length() + 1 + "blksize".length()
                        + 1 + Integer.toString(BLOCK_SIZE).length() + 1);
                buffer.putShort((short) OPCODE_RRQ);
                buffer.put(filename.getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put(MODE_OCTET.getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put("blksize".getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put(Integer.toString(BLOCK_SIZE).getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
            } else {
                this.BLOCK_SIZE = input;
                buffer = ByteBuffer.allocate(2 + filename.length() + 1 + MODE_OCTET.length() + 1 + "blksize".length()
                        + 1 + Integer.toString(BLOCK_SIZE).length() + 1 + "tsize".length() + 1 +
                        +Integer.toString(fileSize).length() + 1);
                buffer.putShort((short) OPCODE_RRQ);
                buffer.put(filename.getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put(MODE_OCTET.getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put("blksize".getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put(Integer.toString(BLOCK_SIZE).getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put("tsize".getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put(Integer.toString(fileSize).getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
            }
        }

        this.buffer = new byte[BLOCK_SIZE + 4];
        byte[] reqBytes = buffer.array();

        try {
            rrqPacket = new DatagramPacket(reqBytes, 0, reqBytes.length, InetAddress.getByName(ipAddr), INIT_TID);
        } catch (Exception e) {
            e.printStackTrace();
        }
        in.close();
        return rrqPacket;
    }

    private static DatagramPacket createACKPacket(int blockNum, String ipAddr, DatagramPacket packet) {
        DatagramPacket ackPacket = null;
        ByteBuffer buffer = ByteBuffer.allocate(2 + 2);
        buffer.putShort((short) OPCODE_ACK);
        buffer.putShort((short) blockNum);
        byte[] ackBytes = buffer.array();

        try {
            ackPacket = new DatagramPacket(ackBytes, 0, ackBytes.length, InetAddress.getByName(ipAddr),
                    packet.getPort());
        } catch (Exception e) {
        }
        return ackPacket;
    }

    private static int getOpcode(DatagramPacket packet) {
        byte[] data = packet.getData();
        int opcode = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        return opcode;
    }

    private static int getBlockNum(DatagramPacket packet) {
        byte[] data = packet.getData();
        int blockNum = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        return blockNum;
    }

    private void displayError(DatagramPacket packet) {
        byte[] data = packet.getData();
        int errorCode = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        String errorMsg = new String(buffer, 4, packet.getLength() - 4);
        System.out.println("[ERROR]: " + errorCode + " " + errorMsg);
    }

    private static int generateSourceTID(int min, int max) {
        Random random = new Random();
        return random.nextInt(max - min + 1) + min;
    }

    public void uploadFile(String filename, String fileSerName, String ipAddr, int timeout) throws IOException {
        DatagramSocket UDPSock = null;
        // Randomly generated source TID
        int sourceTID = generateSourceTID(0, 65535);
        int blockNum = 1;

        // Binds to port number which is equal to randomly generated source TID
        try {
            UDPSock = new DatagramSocket(sourceTID);
        } catch (Exception e) {
            System.out.println("Socket Creation Error" + e.toString());
            return;
        }

        // Creates WRQ Packet
        sendPacket = createWRQPacket(fileSerName, ipAddr, (int) Files.size(Paths.get(filename)));

        // Sends WRQ Packet
        try {
            UDPSock.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (timeout != 0)
            UDPSock.setSoTimeout(timeout);

        // Receives packet from TFTP Server
        recvPacket = new DatagramPacket(buffer, buffer.length);
        try {
            UDPSock.receive(recvPacket);
            if (getOpcode(recvPacket) == OPCODE_ERR)
                displayError(recvPacket);
        } catch (SocketTimeoutException e) {
            // Handle the timeout error
            System.err.println("[ERROR]: Server unresponsive");
            UDPSock.close();
            return;
        }

        int fileSize = -1;
        if (getOpcode(recvPacket) == OPCODE_OACK) {
            byte[] oack = recvPacket.getData();
            byte[] string = new byte[30];
            int offset = 2, index = 0;
            while (offset < recvPacket.getLength()) {
                while (oack[offset] != 0) {
                    string[index] = oack[offset];
                    index++;
                    offset++;
                }
                offset++;

                if (new String(string, 0, index).equals("blksize")) {
                    index = 0;
                    while (oack[offset] != 0) {
                        string[index] = oack[offset];
                        index++;
                        offset++;
                    }
                    offset++;

                    try {
                        BLOCK_SIZE = Integer.parseInt(new String(string, 0, index));
                    } catch (Exception e) {
                        System.err.println(e);
                    }
                } else if (new String(string, 0, index).equals("tsize")) {
                    index = 0;
                    while (oack[offset] != 0) {
                        string[index] = oack[offset];
                        index++;
                        offset++;
                    }
                    offset++;
                    try {
                        fileSize = Integer.parseInt(new String(string, 0, index));
                    } catch (Exception e) {
                        System.err.println(e);
                    }
                }
                index = 0;
            }
        }
        if (getOpcode(recvPacket) == OPCODE_ACK || getOpcode(recvPacket) == OPCODE_OACK) {
            int servTID = recvPacket.getPort();
            int readBytes = 0;
            byte[] dataBlock = new byte[BLOCK_SIZE];
            FileInputStream fio = null;

            try {
                fio = new FileInputStream(filename);
            } catch (FileNotFoundException e) {
                sendPacket = createErrorPacket(5, ipAddr, recvPacket.getPort());
                UDPSock.send(sendPacket);
            }

            // First packet
            readBytes = fio.read(dataBlock);
            sendPacket = createDataPacket(dataBlock, blockNum, ipAddr, servTID, readBytes);
            UDPSock.send(sendPacket);
            blockNum++;

            if (readBytes == BLOCK_SIZE) {
                // Continue to send data packets until there are no more bytes to be read
                while ((readBytes = fio.read(dataBlock)) != -1) {
                    // Receive ACK packet
                    do {
                        UDPSock.receive(recvPacket);
                        if (getOpcode(recvPacket) == OPCODE_ERR)
                            displayError(recvPacket);
                    } while (getBlockNum(recvPacket) != blockNum - 1);

                    // Create DATA packet and sends
                    sendPacket = createDataPacket(dataBlock, blockNum, ipAddr, servTID, readBytes);
                    UDPSock.send(sendPacket);
                    blockNum++;
                }
            }

            if (readBytes == -1 && (int) Files.size(Paths.get(filename)) % BLOCK_SIZE == 0)
                UDPSock.send(createDataPacket(new byte[0], blockNum, ipAddr, servTID, 0));

            fio.close();
            UDPSock.close();
            System.out.println("File uploaded.");
        }
    }

    private DatagramPacket createWRQPacket(String filename, String ipAddr, int fileSize) {
        DatagramPacket wrqPacket = null;
        ByteBuffer buffer = null;
        int input = 0;
        String input2 = null;
        Scanner in = new Scanner(System.in);

        do {
            System.out.println(
                    "Enter block size (8 - 65464). To continue with default block size, input 0.");
            input = Integer.valueOf(in.nextLine());
        } while (!(input >= 8 && input <= 65464 || input == 0));

        do {
            System.out.println("Would you like to include the tsize option (y/n)?");
            input2 = in.nextLine();
        } while (!(input2.equals("y") || input2.equals("n")));

        if (input == 0) {
            if (input2.equals("n")) {
                this.BLOCK_SIZE = 512;
                buffer = ByteBuffer.allocate(2 + filename.length() + 1 + MODE_OCTET.length() + 1);
                buffer.putShort((short) OPCODE_WRQ);
                buffer.put(filename.getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put(MODE_OCTET.getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
            } else {
                this.BLOCK_SIZE = 512;
                buffer = ByteBuffer.allocate(2 + filename.length() + 1 + MODE_OCTET.length() + 1 +
                        "tsize".length() + 1 + Integer.toString(fileSize).length() + 1);
                buffer.putShort((short) OPCODE_WRQ);
                buffer.put(filename.getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put(MODE_OCTET.getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put("tsize".getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put(Integer.toString(fileSize).getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
            }

        } else {
            if (input2.equals("n")) {
                this.BLOCK_SIZE = input;
                buffer = ByteBuffer.allocate(2 + filename.length() + 1 + MODE_OCTET.length() + 1 + "blksize".length()
                        + 1 + Integer.toString(BLOCK_SIZE).length() + 1);
                buffer.putShort((short) OPCODE_WRQ);
                buffer.put(filename.getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put(MODE_OCTET.getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put("blksize".getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put(Integer.toString(BLOCK_SIZE).getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
            } else {
                this.BLOCK_SIZE = input;
                buffer = ByteBuffer.allocate(2 + filename.length() + 1 + MODE_OCTET.length() + 1 + "blksize".length()
                        + 1 + Integer.toString(BLOCK_SIZE).length() + 1 + "tsize".length() + 1 +
                        +Integer.toString(fileSize).length() + 1);
                buffer.putShort((short) OPCODE_WRQ);
                buffer.put(filename.getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put(MODE_OCTET.getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put("blksize".getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put(Integer.toString(BLOCK_SIZE).getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put("tsize".getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
                buffer.put(Integer.toString(fileSize).getBytes(Charset.forName("UTF-8")));
                buffer.put((byte) 0);
            }
        }

        this.buffer = new byte[this.BLOCK_SIZE + 4];
        byte[] reqBytes = buffer.array();

        try {
            wrqPacket = new DatagramPacket(reqBytes, 0, reqBytes.length, InetAddress.getByName(ipAddr), INIT_TID);
        } catch (Exception e) {
        }

        in.close();
        return wrqPacket;
    }

    private static DatagramPacket createDataPacket(byte[] data, int blockNum, String ipAddr, int servTID, int size) {
        DatagramPacket dataPacket = null;

        ByteBuffer buffer = ByteBuffer.allocate(4 + size);
        buffer.putShort((short) OPCODE_DATA);
        buffer.putShort((short) blockNum);
        buffer.put(Arrays.copyOfRange(data, 0, size));
        byte[] pacBytes = buffer.array();

        try {
            dataPacket = new DatagramPacket(pacBytes, 0, pacBytes.length, InetAddress.getByName(ipAddr), servTID);
        } catch (Exception e) {
        }
        return dataPacket;
    }

    private static DatagramPacket createErrorPacket(int errorCode, String ipAddr, int servTID) {
        DatagramPacket errorPacket = null;
        String[] errors = { "Not defined, see error message (if any).", "File not found.", "Access violation.",
                "Disk full or allocation exceeded.", "Illegal TFTP operation.", "Unknown transfer ID.",
                "File already exists.", "No such user." };
        ByteBuffer buffer = ByteBuffer.allocate(4 + errors[errorCode].length() + 1);
        buffer.putShort((short) errorCode);
        buffer.put(errors[errorCode].getBytes(Charset.forName("UTF-8")));
        buffer.put((byte) 0);
        byte[] pacBytes = buffer.array();

        try {
            errorPacket = new DatagramPacket(pacBytes, 0, pacBytes.length, InetAddress.getByName(ipAddr), servTID);
        } catch (Exception e) {
        }
        return errorPacket;
    }

}
