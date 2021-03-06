/*
 * Copyright Dingxuan. All Rights Reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bcia.julongchain.common.ledger.util;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.bcia.julongchain.common.exception.JulongChainException;
import org.bcia.julongchain.common.log.JulongChainLog;
import org.bcia.julongchain.common.log.JulongChainLogFactory;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * io操作辅助类
 *
 * @author wanliangbing, sunzongyu
 * @date 2018/3/9
 * @company Dingxuan
 */
public class IoUtil {
    private static JulongChainLog log = JulongChainLogFactory.getLog(IoUtil.class);
	private static final List<Integer> ALLOW_READ_MODE = new ArrayList<Integer>(){{
		add(4);
		add(5);
		add(6);
		add(7);
	}};
	private static final List<Integer> ALLOW_WRITE_MODE = new ArrayList<Integer>(){{
		add(2);
		add(3);
		add(6);
		add(7);
	}};
	private static final List<Integer> ALLOW_EXECUTE_MODE = new ArrayList<Integer>(){{
		add(1);
		add(3);
		add(5);
		add(7);
	}};

    /**
     * 返回-1:文件不存在
     * >=0:文件大小
     */
    public static long fileExists(String filePath) {
        File file = new File(filePath);
        if(!file.exists()){
            return -1;
        }
        return file.length();
    }

    /**
     * 列出下级目录
     */
    public static List<String> listSubdirs(String dirPath) {
        List<String> list = new ArrayList<>();
        File dir = new File(dirPath);
        if(!dir.exists()){
            log.debug("Dir {} is not exists", dir);
            return null;
        }
		File[] files = dir.listFiles();
		if (files == null) {
			return list;
		}
		for (File file : files) {
            if(file.isDirectory()){
                list.add(file.getName());
            }
        }
        return list;
    }

    /**
     * 修改文件权限 755
     * 默认文件存在
     */
    public static void chmod(File file, int perm){
        int uMod = perm / 100;
        int gMod = (perm % 100) / 10;
        int oMod = perm % 10;
        boolean readable = ALLOW_READ_MODE.contains(uMod);
		boolean writable = ALLOW_WRITE_MODE.contains(uMod);
		boolean executable = ALLOW_EXECUTE_MODE.contains(uMod);
		boolean ownerOnlyReadable = ALLOW_READ_MODE.contains(Math.min(gMod, oMod)) ^ readable;
		boolean ownerOnlyWritable = ALLOW_WRITE_MODE.contains(Math.min(gMod, oMod)) ^ writable;
		boolean ownerOnlyExecutable = ALLOW_EXECUTE_MODE.contains(Math.min(gMod, oMod)) ^ executable;
		if (!file.setReadable(readable, ownerOnlyReadable)) {
			log.error("Can not set read permission to file " + file.getAbsolutePath());
		}
        if (!file.setWritable(writable, ownerOnlyWritable)) {
            log.error("Can not set write permission to file " + file.getAbsolutePath());
        }
        if (!file.setExecutable(executable, ownerOnlyExecutable)) {
            log.error("Can not set execute permission to file " + file.getAbsolutePath());
        }
    }

    /**
     * 创建目录
     */
    public static boolean createDirIfMissing(String dirPath){
        File dir = new File(dirPath);
        if(dir.exists()){
            log.debug("Dir [{}] is already exists", dirPath);
            return true;
        }
        if (!dir.mkdirs()) {
            log.debug("Can not create dir [" + dirPath + "]");
            return false;
        }
        chmod(dir, 644);
        log.debug("Create dir [{}] success", dir.getAbsolutePath());
        return true;
    }

    /**
     * 创建文件
     */
    public static boolean createFileIfMissing(String filePath){
        File file = new File(filePath);
        if(file.exists()){
            log.debug("File [{}] is already exists", filePath);
            return true;
        }
        File dir = file.getParentFile();
        if (!createDirIfMissing(dir.getAbsolutePath())) {
            log.debug("Can not create dir [" + dir.getAbsolutePath() + "]");
            return false;
        }
        try {
            if (file.createNewFile()) {
                chmod(file, 755);
                log.debug("Create file [{}] success", filePath);
                return true;
            } else {
                log.debug("Can not create file [" + filePath + "]");
                return false;
            }
        } catch (IOException e) {
            log.error("Got error error:{} when createing file:[{}]", e.getMessage(), filePath);
            return false;
        }
    }

    /**
     * 序列化对象
     */
    public static byte[] obj2ByteArray(Serializable serializable) throws JulongChainException {
        ByteArrayOutputStream baos = null;
        ObjectOutputStream oos = null;
        try {
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(serializable);
            oos.flush();
            byte[] result = baos.toByteArray();
            baos.close();
            oos.close();
            return result;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new JulongChainException(e);
        }
    }

    /**
     * 反序列化对象
     */
    public static Object byteArray2Obj(byte[] bytes) throws JulongChainException {
        ByteArrayInputStream bais;
        ObjectInputStream ois;
        try {
            bais = new ByteArrayInputStream(bytes);
            ois = new ObjectInputStream(bais);
            Object obj = ois.readObject();
            bais.close();
            ois.close();
            return obj;
        } catch (Exception e){
            log.error(e.getMessage(), e);
            throw new JulongChainException(e);
        }
    }
    /**
     * 获取文件价下所有文件相对文件夹路径
     * @param path 文件夹路径
     * @return
     */
    public static Map<String, File> getFileRelativePath(String path){
        File file = new File(path);
		Map<String, File> result = new TreeMap<>(Comparator.naturalOrder());
        if(!file.exists()){
            log.debug("File {} is not exists", path);
            return result;
        }
        if(!file.isDirectory()){
            log.debug("Input must be a directory, but file {} is not", path);
            return result;
        }
        Queue<File> queue = new LinkedList<>();
        queue.add(file);
        while(queue.size() > 0){
            File tmpFile = queue.poll();
            if(tmpFile.isDirectory()){
                File[] tmpFiles = tmpFile.listFiles();
                if (tmpFiles != null) {
                    queue.addAll(Arrays.asList(tmpFiles));
                }
            } else if(tmpFile.isFile()) {
                result.put(tmpFile.getAbsolutePath().substring(file.getAbsolutePath().length() + 1), tmpFile);
            }
        }
        return result;
    }

    /**
     * 关闭流
     */
    public static void closeStream(Closeable... closeables) throws JulongChainException {
        try {
            for (Closeable closeable : closeables) {
                if(closeable != null){
                    closeable.close();
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JulongChainException(e);
        }
    }

    /**
     * 将文件制作tar包
     * @param files 文件集合
     *              key:文件路径
     *              value:文件对象
     * @param cache 缓冲区大小
     * @return tar 字节流
     */
    public static byte[] tarWriter(Map<String, File> files, int cache) throws JulongChainException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TarArchiveOutputStream taos = new TarArchiveOutputStream(baos);
        taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        FileInputStream fis = null;
        TarArchiveEntry tae;
        try {
            for (Map.Entry<String, File> entry : files.entrySet()) {
                String fileName = entry.getKey();
                File file = entry.getValue();
                fis = new FileInputStream(file);
                tae = new TarArchiveEntry(file);
                tae.setName(fileName);
                taos.putArchiveEntry(tae);
                int num;
                byte[] buff = new byte[cache];
                while((num = fis.read(buff)) != -1){
                    taos.write(buff, 0, num);
                }
                taos.closeArchiveEntry();
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JulongChainException(e);
        } finally {
            closeStream(fis, taos, baos);
        }
        return baos.toByteArray();
    }

    /**
     * 使用gzip压缩文件
     * @param fileBytes 将要压缩的文件流
     * @return gzip压缩的文件流
     */
    public static byte[] gzipWriter(byte[] fileBytes) throws JulongChainException {
        ByteArrayOutputStream baos = null;
        GZIPOutputStream gzos = null;
        try {
            baos = new ByteArrayOutputStream();
            gzos = new GZIPOutputStream(baos);
            gzos.write(fileBytes, 0, fileBytes.length);
            gzos.finish();
            gzos.flush();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JulongChainException(e);
        } finally {
            closeStream(baos, gzos);
        }
        return baos.toByteArray();
    }

    /**
     * 解压缩tar包
     * @param tarBytes tar包字节流
     * @param cache 缓冲大小
     * @return 文件集合
     *          key:文件相对路径
     *          value:文件流
     */
    public static Map<String, byte[]> tarReader(byte[] tarBytes, int cache) throws JulongChainException {
        Map<String, byte[]> result = new HashMap<>(16);
        ByteArrayOutputStream baos = null;
        ByteArrayInputStream bais = null;
        TarArchiveInputStream tais = null;
        try {
            baos = new ByteArrayOutputStream();
            bais = new ByteArrayInputStream(tarBytes);
            tais = new TarArchiveInputStream(bais);
            TarArchiveEntry tae;
            while((tae = tais.getNextTarEntry()) != null){
                int len = 0;
                byte[] buff = new byte[cache];
                while((len = tais.read(buff)) != -1){
                    baos.write(buff, 0, len);
                }
                result.put(tae.getName(), baos.toByteArray());
                baos.reset();
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JulongChainException(e);
        } finally {
            closeStream( tais, bais, baos);
        }
        return result;
    }

    /**
     * 使用gzip解压文件
     * @param fileBytes 文件流
     * @param cache 缓冲大小
     * @return 解压后文件流
     */
    public static byte[] gzipReader(byte[] fileBytes, int cache) throws JulongChainException {
        ByteArrayOutputStream baos = null;
        ByteArrayInputStream bais = null;
        GZIPInputStream gzis = null;
        try {
            bais = new ByteArrayInputStream(fileBytes);
            gzis = new GZIPInputStream(bais);
            baos = new ByteArrayOutputStream();
            byte[] buff = new byte[cache];
            int num = 0;
            while((num = gzis.read(buff)) > 0){
                baos.write(buff, 0, num);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JulongChainException(e);
        } finally {
            closeStream( bais, baos, gzis);
        }
        return baos.toByteArray();
    }

    /**
     * 输出解压缩的文件
     * @param files 文件集合
     *              key: 文件相对路径
     *              value: 文件流
     * @param outputPath 输出路径
     */
    public static void fileWriter(Map<String, byte[]> files, String outputPath) throws JulongChainException {
        outputPath = outputPath.endsWith(File.separator) ? outputPath : outputPath + File.separator;
        FileOutputStream fos = null;
        try {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                String fileRelativePath = entry.getKey();
                byte[] fileBytes = entry.getValue();
                File file = new File(outputPath + fileRelativePath);
                File dir = file.getParentFile();
                if (!createDirIfMissing(dir.getAbsolutePath())) {
                    throw new JulongChainException("Can not create dir " + dir.getAbsolutePath());
                }
                fos = new FileOutputStream(file);
                fos.write(fileBytes);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new JulongChainException(e);
        } finally {
            closeStream(fos);
        }
    }

    /**
     * 将文件读取为流形式
     * @param filePath 文件路径
     * @param cache 缓冲大小
     */
    public static byte[] fileReader(String filePath, int cache) throws JulongChainException {
        File file = new File(filePath);
        if (!file.exists()) {
            log.debug("File {} not found", filePath);
            return null;
        }
        FileInputStream fis = null;
        ByteArrayOutputStream baos = null;
        try{
            fis = new FileInputStream(file);
            baos = new ByteArrayOutputStream();
            int num = 0;
            byte[] bytes = new byte[cache];
            while((num = fis.read(bytes)) > 0){
                baos.write(bytes, 0, num);
            }
            return baos.toByteArray();
        } catch (Exception e){
            log.error(e.getMessage(), e);
            throw new JulongChainException(e);
        } finally {
            closeStream(fis, baos);
        }
    }
}
