protoc -I=./ --proto_path=./ --java_out=../city/shared/src/main/java ./gaCode.proto
protoc -I=./ --proto_path=./ --proto_path=../../city_proto25/ --java_out=../city/shared/src/main/java ./ga.proto
protoc -I=./ --proto_path=./ --java_out=../city/gs/src/main/java ./db.proto
pause