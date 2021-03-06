package com.ai.paas.cpaas.rm.manage.service.flannel;

import java.io.InputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import com.ai.paas.cpaas.rm.util.AnsibleCommand;
import com.ai.paas.cpaas.rm.util.ExceptionCodeConstants;
import com.ai.paas.cpaas.rm.util.TaskUtil;
import com.ai.paas.cpaas.rm.vo.MesosInstance;
import com.ai.paas.cpaas.rm.vo.OpenResourceParamVo;
import com.ai.paas.ipaas.PaasException;
import com.esotericsoftware.minlog.Log;

public class OpenFlannelPort implements Tasklet {

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext)
      throws Exception {
    InputStream in =
        OpenFlannelPort.class.getResourceAsStream("/playbook/flannel/openflannelport.yml");
    String content = TaskUtil.getFile(in);
    OpenResourceParamVo openParam = TaskUtil.createOpenParam(chunkContext);
    Boolean useAgent = openParam.getUseAgent();
    String aid = openParam.getAid();
    TaskUtil.uploadFile("openflannelport.yml", content, useAgent, aid);
    List<MesosInstance> mesosMaster = openParam.getMesosMaster();
    MesosInstance masternode = mesosMaster.get(0);
    String password = masternode.getPasswd();
    List<String> vars = new ArrayList<String>();
    vars.add("ansible_ssh_pass=" + password);
    vars.add("ansible_become_pass=" + password);
    AnsibleCommand command =
        new AnsibleCommand(TaskUtil.getSystemProperty("filepath") + "/openflannelport.yml", "root",
            vars);
    Timestamp start = new Timestamp(System.currentTimeMillis());

    String result = new String();
    try {
      result = TaskUtil.executeFile("openflannelport", command.toString(), useAgent, aid);
    } catch (Exception e) {
      Log.error(e.toString());
      result = e.toString();
      throw new PaasException(ExceptionCodeConstants.DubboServiceCode.SYSTEM_ERROR_CODE,
          e.toString());
    } finally {
      // insert log and task record
      int taskId =
          TaskUtil.insertResJobDetail(start, openParam.getClusterId(), command.toString(), 27);
      TaskUtil.insertResTaskLog(openParam.getClusterId(), taskId, result);

    }

    return RepeatStatus.FINISHED;
  }

}
