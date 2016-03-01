package com.ai.paas.cpaas.rm.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.ClientProtocolException;
import org.springframework.batch.core.scope.context.ChunkContext;

import com.ai.paas.cpaas.rm.vo.MesosInstance;
import com.ai.paas.cpaas.rm.vo.OpenResourceParamVo;
import com.ai.paas.ipaas.PaasException;

public class VerifyWebService {

  public static void checkwebService(ChunkContext chunkContext, String port, String filename,
      String path) throws ClientProtocolException, IOException, PaasException {
    OpenResourceParamVo openParam = TaskUtil.createOpenParam(chunkContext);
    InputStream in = OpenPortUtil.class.getResourceAsStream(path);
    String content = TaskUtil.getFile(in);
    Boolean useAgent = openParam.getUseAgent();
    TaskUtil.uploadFile(filename, content, useAgent);


    List<MesosInstance> mesosMaster = openParam.getMesosMaster();
    List<String> vars = new ArrayList<String>();
    MesosInstance masternode = mesosMaster.get(0);
    String passwd = masternode.getPasswd();
    vars.add("ansible_ssh_pass=" + passwd);
    vars.add("ansible_become_pass=" + passwd);
    StringBuffer inventory_hosts = new StringBuffer();
    inventory_hosts.append("[");
    inventory_hosts.append("'" + masternode.getIp() + "'");
    for (int i = 1; i < mesosMaster.size(); i++) {
      inventory_hosts.append(",");
      inventory_hosts.append("'").append(mesosMaster.get(i).getIp()).append("'");
    }
    inventory_hosts.append("]");
    vars.add("inventory_hosts=" + inventory_hosts.toString());
    AnsibleCommand command =
        new AnsibleCommand(TaskUtil.getSystemProperty("filepath") + "/" + filename, "root", vars);
    TaskUtil.executeFile(filename + ".sh", command.toString(), useAgent);
    // TaskUtil.executeCommand(command.toString(), useAgent);
  }

}
