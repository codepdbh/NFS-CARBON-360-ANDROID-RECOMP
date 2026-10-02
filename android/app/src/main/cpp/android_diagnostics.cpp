#include <jni.h>
#include <vulkan/vulkan.h>
#include <string>
#include <vector>

namespace {
std::string Quote(const char* text) {
  std::string result = "\"";
  for (; *text; ++text) {
    if (*text == '\\' || *text == '"') result += '\\';
    if (static_cast<unsigned char>(*text) >= 32) result += *text;
  }
  return result + '"';
}
std::string Probe() {
  VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
  app.pApplicationName = "Carbon Vulkan diagnostics";
  app.apiVersion = VK_API_VERSION_1_0;
  VkInstanceCreateInfo create{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
  create.pApplicationInfo = &app;
  VkInstance instance{};
  if (vkCreateInstance(&create, nullptr, &instance) != VK_SUCCESS)
    return R"({"compatible":false,"probeError":"Vulkan instance creation failed"})";
  uint32_t count = 0;
  auto status = vkEnumeratePhysicalDevices(instance, &count, nullptr);
  std::string report = R"({"compatible":false,"probeError":"No Vulkan device"})";
  if (status == VK_SUCCESS && count) {
    std::vector<VkPhysicalDevice> devices(count);
    if (vkEnumeratePhysicalDevices(instance, &count, devices.data()) == VK_SUCCESS && count) {
      VkPhysicalDeviceProperties properties{};
      vkGetPhysicalDeviceProperties(devices[0], &properties);
      auto version = std::to_string(VK_VERSION_MAJOR(properties.apiVersion)) + "." +
          std::to_string(VK_VERSION_MINOR(properties.apiVersion)) + "." +
          std::to_string(VK_VERSION_PATCH(properties.apiVersion));
      report = "{\"gpu\":" + Quote(properties.deviceName) + ",\"vulkan\":" + Quote(version.c_str()) +
          ",\"driverVersion\":" + std::to_string(properties.driverVersion) +
          ",\"vendorId\":" + std::to_string(properties.vendorID) +
          ",\"checkedRenderer\":\"xenos\",\"compatible\":true," +
          "\"note\":\"GPU presence only; runtime checks required features\"}";
    }
  }
  vkDestroyInstance(instance, nullptr);
  return report;
}
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_nfscarbon_android_Diagnostics_nativeGpuReport(JNIEnv* env, jclass) {
  return env->NewStringUTF(Probe().c_str());
}
