/**
 * Copyright DingXuan. All Rights Reserved.
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
package org.bcia.javachain.consenter.consensus.singleton;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ProtocolStringList;
import io.grpc.okhttp.internal.Protocol;
import org.bcia.javachain.common.exception.*;
import org.bcia.javachain.common.log.JavaChainLog;
import org.bcia.javachain.common.log.JavaChainLogFactory;
import org.bcia.javachain.common.util.ValidateUtils;
import org.bcia.javachain.common.util.producer.Consumer;
import org.bcia.javachain.common.util.producer.Producer;
import org.bcia.javachain.consenter.common.multigroup.ChainSupport;
import org.bcia.javachain.consenter.consensus.IChain;
import org.bcia.javachain.consenter.consensus.IConsensue;
import org.bcia.javachain.consenter.consensus.IConsenterSupport;
import org.bcia.javachain.consenter.entity.BatchesMes;
import org.bcia.javachain.protos.common.Common;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author zhangmingyang
 * @Date: 2018/3/7
 * @company Dingxuan
 */
public class Singleton implements IChain, IConsensue {
    private BlockingQueue<Common.Envelope> blockingQueue;
    private Producer<Common.Envelope> producer;
    private Consumer<Common.Envelope> consumer;
    private static Singleton instance;
    private static JavaChainLog log = JavaChainLogFactory.getLog(Singleton.class);
    private ChainSupport support;
   // private IConsenterSupport support;
    private Message normalMessage;
    private Message configMessage;

    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();
                }
            }
        }

        return instance;
    }

    @Override
    public void order(Common.Envelope env, long configSeq) {
        //排序处理普通消息
        Message message = new Message(configSeq, env);
        normalMessage = message;
    }

    @Override
    public void configure(Common.Envelope config, long configSeq) {
        Message message = new Message(configSeq, config);
        configMessage = message;
    }

    @Override
    public void waitReady() {
        return;
    }

    @Override
    public void start() {
        consumer.start();
    }

    @Override
    public void halt() {

    }

    @Override
    public IChain handleChain(ChainSupport consenterSupport, Common.Metadata metadata) {
        return new Singleton(consenterSupport);
    }


    public Singleton(ChainSupport support) {
        this.support = support;
    }


    public Singleton() {
        instance = this;
        blockingQueue = new LinkedBlockingQueue<>();
        producer = new Producer<>(blockingQueue);
        consumer = new Consumer<Common.Envelope>(blockingQueue) {
            @Override
            public boolean consume(Common.Envelope envelope) {
                try {
                    doProcess(envelope);
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                } catch (LedgerException e) {
                    e.printStackTrace();
                } catch (ValidateException e) {
                    e.printStackTrace();
                } catch (PolicyException e) {
                    e.printStackTrace();
                }
                return true;
            }
        };
        start();
    }

    //处理剪块操作
    public void doProcess(Common.Envelope envelope) throws InvalidProtocolBufferException, LedgerException, ValidateException, PolicyException {
        long timer = 0;
        long seq = support.getSequence();
        if (configMessage == null) {//普通消息
            if (normalMessage.getConfigSeq() < seq) {
                try {
                    support.getProcessor().processNormalMsg(normalMessage.getMessage());
                    //support.processNormalMsg(normalMessage.getMessage());
                } catch (InvalidProtocolBufferException e) {
                    log.warn(String.format("Discarding bad normal message: %s", e.getMessage()));
                }
            }
            BatchesMes batchesMes = support.getCutter().ordered(normalMessage.message);
           // BatchesMes batchesMes = support.blockCutter().ordered(normalMessage.message);
            Common.Envelope[][] batches = batchesMes.getMessageBatches();
            if(batches.length==0&&timer==0){
                timer= support.getLedgerResources().getMutableResources().getGroupConfig().getConsenterConfig().getBatchTimeout();
                //timer=support.sharedConfig().getGroupConfig().getConsenterConfig().getBatchTimeout();
            }
            for (Common.Envelope[] env:batches) {
                Common.Block block=support.createNextBlock(env);
                support.writeBlock(block,null);
            }if(batches.length>0){
                timer=0;
            }
        } else {
            if(configMessage.getConfigSeq()<seq){
                try {
                    support.getProcessor().processConfigMsg(configMessage.getMessage());
                 //   support.processConfigMsg(configMessage.getMessage());
                } catch (ConsenterException e) {
                } catch (InvalidProtocolBufferException e) {
                    log.warn(String.format("Discarding bad config message: %s", e.getMessage()));
                } catch (ValidateException e) {
                } catch (PolicyException e) {
                }
               Common.Envelope[] batch=support.getCutter().cut();
                if(batch!=null){
                    Common.Block block= support.createNextBlock(batch);
                    support.writeBlock(block,null);
                }
                Common.Block block=support.createNextBlock(new Common.Envelope[]{configMessage.getMessage()});
                support.writeConfigBlock(block,null);
                timer=0;
            }
           if(timer>0){
                timer=0;
                Common.Envelope[] batch=support.getCutter().cut();
                if(batch.length==0){
                    log.warn("Batch timer expired with no pending requests, this might indicate a bug");
                }
                log.debug("Batch timer expired, creating block");
               Common.Block block=support.createNextBlock(batch);
               support.writeBlock(block,null);
            }

        }


        System.out.println(envelope.getPayload().toStringUtf8());
    }

    public boolean pushToQueue(Common.Envelope envelope) throws ValidateException {
        ValidateUtils.isNotNull(envelope, "event can not be null");
        return producer.produce(envelope);
    }

    public static void main(String[] args) throws ValidateException, ConsenterException {
        Singleton singleton = null;
        singleton = Singleton.getInstance();

        Common.Envelope envelope = Common.Envelope.newBuilder().setPayload(ByteString.copyFrom("123".getBytes())).build();
        singleton.order(envelope,1);
        singleton.pushToQueue(envelope);
    }


    class Message {
        long configSeq;
        Common.Envelope message;

        public Message(long configSeq, Common.Envelope message) {
            this.configSeq = configSeq;
            this.message = message;
        }

        public long getConfigSeq() {
            return configSeq;
        }

        public Common.Envelope getMessage() {
            return message;
        }
    }

    public IConsenterSupport getSupport() {
        return support;
    }

    public Message getNormalMessage() {
        return normalMessage;
    }

    public Message getConfigMessage() {
        return configMessage;
    }
}
