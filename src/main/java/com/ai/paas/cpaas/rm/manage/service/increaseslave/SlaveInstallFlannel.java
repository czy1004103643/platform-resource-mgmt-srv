package com.ai.paas.cpaas.rm.manage.service.increaseslave;

import java.io.InputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import com.ai.paas.cpaas.rm.manage.service.flannel.FlannelInstall;
import com.ai.paas.cpaas.rm.util.AnsibleCommand;
import com.ai.paas.cpaas.rm.util.ExceptionCodeConstants;
import com.ai.paas.cpaas.rm.util.TaskUtil;
import com.ai.paas.cpaas.rm.vo.MesosInstance;
import com.ai.paas.cpaas.rm.vo.MesosSlave;
import com.ai.paas.cpaas.rm.vo.OpenResourceParamVo;
import com.ai.paas.ipaas.PaasException;

public class SlaveInstallFlannel implements Tasklet {
  private static Logger logger = Logger.getLogger(FlannelInstall.class);

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext)
      throws Exception {
    logger.info("install flannel step ");
    InputStream in =
        FlannelInstall.class.getResourceAsStream("/playbook/flannel/flannelinstall.yml");
    String content = TaskUtil.getFile(in);
    OpenResourceParamVo openParam = TaskUtil.createOpenParam(chunkContext);
    String aid = openParam.getAid();
    Boolean useAgent = openParam.getUseAgent();
    TaskUtil.uploadFile("flannelinstall.yml", content, useAgent, aid);
    List<MesosInstance> mesosMaster = openParam.getMesosMaster();
    List<MesosSlave> slaveList = openParam.getMesosSlave();
    StringBuffer shellContext = TaskUtil.createBashFile();

    MesosInstance masternode = mesosMaster.get(0);
    StringBuffer flannelEtcd = new StringBuffer("FLANNEL_ETCD=");
    flannelEtcd.append("http://" + masternode.getIp()).append(":2379");
    for (int i = 1; i < mesosMaster.size(); i++) {
      flannelEtcd.append(",http://");
      flannelEtcd.append(mesosMaster.get(i).getIp()).append(":2379");
    }
    String password = slaveList.get(0).getPasswd();
    // It is necessary assign the location of docker startup,and append the addr of etcd master
    StringBuffer execstart = new StringBuffer("execstart='ExecStart=/usr/bin/docker daemon -g  ");
    execstart.append(openParam.getImagePath()).append("  -H fd:// --cluster-store=etcd://");
    execstart.append(masternode.getIp());
    execstart.append("'");

    for (MesosSlave instance : slaveList) {
      int id = instance.getId();
      this.genCommand(instance, openParam, shellContext, flannelEtcd.toString(),
          execstart.toString(), password, id, "slave");
    }
    Timestamp start = new Timestamp(System.currentTimeMillis());

    String result = new String();
    int status = TaskUtil.FINISHED;
    try {
      result = TaskUtil.executeFile("flannelinstall", shellContext.toString(), useAgent, aid);
    } catch (Exception e) {
      logger.error("install flannel:", e);
      result = e.toString();
      status = TaskUtil.FAILED;
      throw new PaasException(ExceptionCodeConstants.DubboServiceCode.SYSTEM_ERROR_CODE,
          e.toString());
    } finally {
      // insert log and task record
      int taskId =
          TaskUtil.insertResJobDetail(start, openParam.getClusterId(), shellContext.toString(),
              TaskUtil.getTypeId("slaveInstallFlannel"), status);
      TaskUtil.insertResTaskLog(openParam.getClusterId(), taskId, result);
    }

    return RepeatStatus.FINISHED;
  }

  public void genCommand(MesosInstance instance, OpenResourceParamVo openParam,
      StringBuffer shellContext, String flannelEtcd, String execstart, String password, int i,
      String type) {
    List<String> vars = new ArrayList<String>();
    vars.add("ansible_ssh_pass=" + password);
    vars.add("ansible_become_pass=" + password);
    if (type.equals("slave")) {
      vars.add("hosts=" + TaskUtil.genSlaveName(i));
    } else {
      vars.add("hosts=" + TaskUtil.genMasterName(i));
    }
    vars.add("flanneletcd='" + flannelEtcd.toString() + "'");
    vars.add("flanneletcdkey='FLANNEL_ETCD_KEY=" + "/" + openParam.getClusterId() + "/"
        + instance.getZone() + "'");
    vars.add(execstart);
    AnsibleCommand command =
        new AnsibleCommand(TaskUtil.getSystemProperty("filepath") + "/flannelinstall.yml", "root",
            vars);
    shellContext.append(command.toString()).append("\n");
  }

}