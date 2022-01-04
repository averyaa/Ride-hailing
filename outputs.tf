# output variables is a way to organize data to be easily queried and
# shown back to the Terraform user.
#
# As a user of Terraform, you may be interested in values of importance,
# e.g. a load balancer IP, VPN address, etc.
#
# Outputs are a way to tell Terraform what data is important.
# This data is outputted when "terraform apply" is called,
# and can be queried using the "terraform output" command.

output master_public_dns {
  description = "The public DNS name of the master EC2 instance"
  value       = "${aws_emr_cluster.emr-test-cluster.master_public_dns}"
}

output "workspace_public_dns" {
  description = "The public DNS name of your student workspace instance"
  value       = "${aws_instance.student_on_demand_instance.public_dns}"
}
output log_uri {
  description = "The path to the Amazon S3 location where logs for this cluster are stored"
  value       = "${aws_emr_cluster.emr-test-cluster.log_uri}"
}
