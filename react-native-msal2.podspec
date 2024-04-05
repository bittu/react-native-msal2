require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "react-native-msal2"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => "12.0" }
  s.source       = { :git => "https://github.com/bittu/react-native-msal2.git", :tag => "v#{s.version}" }

  s.source_files = "ios/**/*.{h,m}"

  s.dependency "React-Core"
  s.dependency "MSAL"
end
