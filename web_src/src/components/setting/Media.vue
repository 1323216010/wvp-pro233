<template>
  <div id="SettingForMedia">
  <el-container>
    <el-header>
      <uiHeader></uiHeader>
    </el-header>
    <el-main>
      <div style="background-color: #FFFFFF; margin-bottom: 1rem; position: relative; padding: 0.5rem; text-align: left;">
        <span style="font-size: 1rem; font-weight: bold;">媒体服务</span>
      </div>
      <div style="width: 60%; margin:0 auto; background-color: #FFFFFF; position: relative; padding: 5rem 6.5rem; text-align: left;font-size: 14px; max-width: 400px">
        <el-form ref="form" :rules="rules" :model="form" label-width="140px">
          <el-form-item label="IP" prop="IP">
            <el-input v-model="form.IP" clearable></el-input>
          </el-form-item>
          <el-form-item label="公网IP" prop="wanIp">
            <el-input v-model="form.wanIp" clearable></el-input>
          </el-form-item>
          <el-form-item label="HOOK IP" prop="hookIp">
            <el-input v-model="form.hookIp" clearable></el-input>
          </el-form-item>
          <el-form-item label="HTTP端口" >
            <el-input v-model.number="form.httpPort" clearable></el-input>
          </el-form-item>
          <el-form-item label="HTTP SSL端口" >
            <el-input v-model.number="form.httpSSlPort" clearable></el-input>
          </el-form-item>
          <el-form-item label="RTMP端口" >
            <el-input v-model.number="form.rtmpPort" clearable></el-input>
          </el-form-item>
          <el-form-item label="RTMP SSL端口" >
            <el-input v-model.number="form.rtmpSSlPort" clearable></el-input>
          </el-form-item>
          <el-form-item label="RTSP端口" >
            <el-input v-model.number="form.rtspPort" clearable></el-input>
          </el-form-item>
          <el-form-item label="RTSP SSL端口" >
            <el-input v-model.number="form.rtspSSLPort" clearable></el-input>
          </el-form-item>
          <el-form-item label="RTP端口" >
            <el-input v-model.number="form.rtpProxyPort" clearable></el-input>
          </el-form-item>
          <el-form-item label="自动点播" >
            <el-switch v-model="form.autoApplyPlay"></el-switch>
          </el-form-item>
          <el-form-item label="接口密钥" prop="secret">
            <el-input v-model="form.secret" clearable></el-input>
          </el-form-item>
          <el-form-item label="无人观看触发时长" >
            <el-input v-model.number="form.streamNoneReaderDelayMS" clearable></el-input>
          </el-form-item>
          <el-form-item label="自动配置" >
            <el-switch v-model="form.autoConfig"></el-switch>
          </el-form-item>
          <el-form-item label="使用多端口" >
            <el-switch v-model="form.rtp.enable"></el-switch>
          </el-form-item>
          <el-form-item label="端口范围" >
            <el-input v-model.number="form.rtp.portRange" clearable></el-input>
          </el-form-item>
          <el-form-item label="DOCKER智能识别">
            <el-input type="textarea" v-model="dockerStr"></el-input>
          </el-form-item>

          <el-form-item>
            <div style="float: right;">
              <el-button type="primary" @click="onSubmit">保存</el-button>
<!--              <el-button @click="close">取消</el-button>-->
            </div>

          </el-form-item>
        </el-form>
      </div>


    </el-main>
  </el-container>
  </div>
</template>

<script>
import uiHeader from '../UiHeader.vue'
export default {
  name: "SettingForMedia",
  components: {
    uiHeader
  },
  data() {
    return {
      dockerStr: null,
      form: {
        ip: null,
        wanIp: null,
        hookIp: null,
        httpPort: null,
        httpSSlPort: null,
        rtmpPort: null,
        rtpProxyPort: null,
        rtspPort: null,
        rtspSSLPort: null,
        autoConfig: true,
        secret: "035c73f7-bb6b-4889-a715-d9eb2d1925cc",
        rtp: {
          enable: false,
          portRange: null
        },
      },

      rules: {
        IP: [{ required: true, message: "请输入名称", trigger: "blur" }],
        wanIp: [{ required: false, message: "请输入应用名", trigger: "blur" }],
        hookIp: [{ required: false, message: "请输入流ID", trigger: "blur" }],
      },
    }
  }
}
</script>

<style scoped>

</style>
